package jetbrains.buildServer.commitPublisher.gitlab;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import java.util.LinkedHashMap;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

class GitlabPublisher extends HttpBasedCommitStatusPublisher {

  private static final String REFS_HEADS = "refs/heads/";
  private static final String REFS_TAGS = "refs/tags/";
  private static final Logger LOG = Logger.getInstance(GitlabPublisher.class.getName());
  private final Gson myGson = new Gson();

  private final WebLinks myLinks;

  GitlabPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                  @NotNull ExecutorServices executorServices, @NotNull WebLinks links,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, executorServices, params, problems);
    myLinks = links;
  }


  @NotNull
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
  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GitlabBuildStatus.RUNNING, "Build started");
    return true;
  }


  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    GitlabBuildStatus status = build.getBuildStatus().isSuccessful() ? GitlabBuildStatus.SUCCESS : GitlabBuildStatus.FAILED;
    publish(build, revision, status, build.getStatusDescriptor().getText());
    return true;
  }


  @Override
  public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GitlabBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build, revision, buildInProgress ? GitlabBuildStatus.RUNNING : GitlabBuildStatus.SUCCESS, "Build marked as successful");
    return true;
  }


  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GitlabBuildStatus.CANCELED, build.getStatusDescriptor().getText());
    return true;
  }


  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull GitlabBuildStatus status,
                       @NotNull String description) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing GitLab API URL parameter");
    String pathPrefix = GitlabSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());

    String message = createMessage(status, build, revision, myLinks.getViewResultsUrl(build), description);
    try {
      publish(revision.getRevision(), message, repository, LogUtil.describe(build));
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to GitLab for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  private void publish(@NotNull String commit, @NotNull String data, @NotNull Repository repository, @NotNull String buildDescription) throws Exception {
    String url = GitlabSettings.getProjectsUrl(getApiUrl(), repository.owner(), repository.repositoryName()) + "/statuses/" + commit;
    LOG.debug("Request url: " + url + ", message: " + data);
    postAsync(url, null, null, data, ContentType.APPLICATION_JSON, Collections.singletonMap("PRIVATE-TOKEN", getPrivateToken()), buildDescription);
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

    final Map<String, String> data = new LinkedHashMap<String, String>();
    data.put("state", status.getName());
    data.put("name", build.getBuildTypeName());
    data.put("target_url", url);
    data.put("description", description);
    if (ref != null)
      data.put("ref", ref);
    return myGson.toJson(data);
  }

  @Nullable
  static Repository parseRepository(@NotNull VcsRoot root, @Nullable String pathPrefix) {
    if ("jetbrains.git".equals(root.getVcsName())) {
      String url = root.getProperty("url");
      return url == null ? null : GitRepositoryParser.parseRepository(url, pathPrefix);
    } else {
      return null;
    }
  }


  private String getApiUrl() {
    return HttpHelper.stripTrailingSlash(myParams.get(Constants.GITLAB_API_URL));
  }

  private String getPrivateToken() {
    return myParams.get(Constants.GITLAB_TOKEN);
  }

}
