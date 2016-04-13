package jetbrains.buildServer.commitPublisher.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.BuildChangesLoaderContext;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class GitlabPublisher extends BaseCommitStatusPublisher {

  private static final String REFS_HEADS = "refs/heads/";
  private static final String REFS_TAGS = "refs/tags/";
  private static final Logger LOG = Logger.getInstance(GitlabPublisher.class.getName());

  private final WebLinks myLinks;
  private final RepositoryStateManager myRepositoryStateManager;

  public GitlabPublisher(@NotNull WebLinks links,
                         @NotNull RepositoryStateManager repositoryStateManager,
                         @NotNull Map<String, String> params) {
    super(params);
    myLinks = links;
    myRepositoryStateManager = repositoryStateManager;
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
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, GitlabBuildStatus.SUCCESS, "Build marked as successful");
    return true;
  }


  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, GitlabBuildStatus.CANCELED, build.getStatusDescriptor().getText());
    return true;
  }


  @Override
  public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, GitlabBuildStatus.FAILED, build.getStatusDescriptor().getText());
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
    BuildChangesLoaderContext.getVcsBranchName(myRepositoryStateManager, build.getBranch(), build.getBuildType(), root);
    String message = createMessage(status, build, root, myLinks.getViewResultsUrl(build), description);
    try {
      publish(revision.getRevision(), message, repository);
    } catch (Exception e) {
      throw new PublishError("Cannot publish status to GitLab for VCS root " +
              revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }


  private void publish(@NotNull String commit, @NotNull String data, @NotNull Repository repository) throws Exception {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpPost post = null;
    HttpResponse response = null;
    try {
      String url = getApiUrl() + "/projects/" + repository.owner() + "%2F" + repository.repositoryName() + "/statuses/" + commit;
      LOG.debug("Request url: " + url + ", message: " + data);
      post = new HttpPost(url);
      post.addHeader("PRIVATE-TOKEN", getPrivateToken());
      post.setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));
      response = client.execute(post);
      StatusLine statusLine = response.getStatusLine();
      LOG.debug("Response: " + statusLine);
      if (statusLine.getStatusCode() >= 400)
        throw new PublishError("Error while publishing status, response status " + statusLine);
    } finally {
      HttpClientUtils.closeQuietly(response);
      releaseConnection(post);
      HttpClientUtils.closeQuietly(client);
    }
  }


  @NotNull
  private String createMessage(@NotNull GitlabBuildStatus status,
                               @NotNull SBuild build,
                               @NotNull VcsRootInstance root,
                               @NotNull String url,
                               @NotNull String description) {
    SBuildType buildType = build.getBuildType();
    String ref = null;
    if (buildType != null) {
      ref = BuildChangesLoaderContext.getVcsBranchName(myRepositoryStateManager, build.getBranch(), build.getBuildType(), root);
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

  String getPrivateToken() {
    return myParams.get(Constants.GITLAB_TOKEN);
  }

  private void releaseConnection(@Nullable HttpPost post) {
    if (post != null) {
      try {
        post.releaseConnection();
      } catch (Exception e) {
        LOG.warn("Error releasing connection", e);
      }
    }
  }

}
