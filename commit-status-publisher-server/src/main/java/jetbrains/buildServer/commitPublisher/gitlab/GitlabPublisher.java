package jetbrains.buildServer.commitPublisher.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class GitlabPublisher extends HttpBasedCommitStatusPublisher {

  private static final String REFS_HEADS = "refs/heads/";
  private static final String REFS_TAGS = "refs/tags/";
  private static final Logger LOG = Logger.getInstance(GitlabPublisher.class.getName());

  private final WebLinks myLinks;

  public GitlabPublisher(@NotNull ExecutorServices executorServices, @NotNull WebLinks links, @NotNull Map<String, String> params) {
    super(executorServices, params);
    myLinks = links;
  }


  @Override
  public String getId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }


  @NotNull
  @Override
  public String toString() {
    return "GitLab";
  }


  @Override
  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, GitlabBuildStatus.RUNNING, "Build started");
    return true;
  }


  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    GitlabBuildStatus status = build.getBuildStatus().isSuccessful() ? GitlabBuildStatus.SUCCESS : GitlabBuildStatus.FAILED;
    publish(build, revision, status, build.getStatusDescriptor().getText());
    return true;
  }


  @Override
  public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, GitlabBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) {
    publish(build, revision, buildInProgress ? GitlabBuildStatus.RUNNING : GitlabBuildStatus.SUCCESS, "Build marked as successful");
    return true;
  }


  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, GitlabBuildStatus.CANCELED, build.getStatusDescriptor().getText());
    return true;
  }


  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull GitlabBuildStatus status,
                       @NotNull String description) throws PublishError {
    VcsRootInstance root = revision.getRoot();
    Repository repository = parseRepository(root);
    if (repository == null)
      throw new PublishError("Cannot parse repository from VCS root url " + root.getName());

    String message = createMessage(status, build, revision, myLinks.getViewResultsUrl(build), description);
    try {
      publish(revision.getRevision(), message, repository);
    } catch (Exception e) {
      throw new PublishError("Cannot publish status to GitLab for VCS root " +
              revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  private void publish(@NotNull String commit, @NotNull String data, @NotNull Repository repository) throws Exception {
    String url = getApiUrl() + "/projects/" + repository.owner() + "%2F" + repository.repositoryName() + "/statuses/" + commit;
    LOG.debug("Request url: " + url + ", message: " + data);
    postAsync(url, null, null, data, ContentType.APPLICATION_JSON, Collections.singletonMap("PRIVATE-TOKEN", getPrivateToken()));
  }

  @NotNull
  private String createMessage(@NotNull GitlabBuildStatus status,
                               @NotNull SBuild build,
                               @NotNull BuildRevision revision,
                               @NotNull String url,
                               @NotNull String description) {

    RepositoryVersion repositoryVersion = revision.getRepositoryVersion();
    String ref = repositoryVersion.getVcsBranch();
    if (ref != null) {
      if (ref.startsWith(REFS_HEADS)) {
        ref = ref.substring(REFS_HEADS.length());
      } else if (ref.startsWith(REFS_TAGS)) {
        ref = ref.substring(REFS_TAGS.length());
      } else {
        ref = null;
      }
    }

    StringBuilder result = new StringBuilder();
    result.append("{").append("\"state\":").append("\"").append(status.getName()).append("\",")
            .append("\"name\":").append("\"").append(build.getBuildTypeName()).append("\",")
            .append("\"target_url\":").append("\"").append(url).append("\",")
            .append("\"description\":").append("\"").append(description).append("\"");
    if (ref != null)
      result.append(",\"ref\":").append("\"").append(escape(ref)).append("\"");
    result.append("}");
    return result.toString();
  }


  @NotNull
  private String escape(@NotNull String str) {
    String result = WebUtil.escapeForJavaScript(str, false, false);
    return result.replaceAll("\\\\'", "'");
  }


  @Nullable
  private Repository parseRepository(@NotNull VcsRootInstance root) {
    if ("jetbrains.git".equals(root.getVcsName())) {
      String url = root.getProperty("url");
      return url == null ? null : GitRepositoryParser.parseRepository(url);
    } else {
      return null;
    }
  }


  String getApiUrl() {
    return myParams.get(Constants.GITLAB_API_URL);
  }

  private String getPrivateToken() {
    return myParams.get(Constants.GITLAB_TOKEN);
  }

}
