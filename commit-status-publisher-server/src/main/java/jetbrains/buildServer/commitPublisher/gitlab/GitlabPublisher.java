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

import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabMRInfo;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.util.*;

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

    Pattern r = Pattern.compile("/merge-requests/([0-9]+)/");
    Matcher m = r.matcher(revision.getRepositoryVersion().getVcsBranch());
    if (m.find( )) {
      String mr_number = m.group(1);

      String url = GitlabSettings.getProjectsUrl(getApiUrl(), repository.owner(), repository.repositoryName()) + "/merge_requests/" + mr_number;

      MRInfoResponseProcessor processorMR = new MRInfoResponseProcessor(); 

      try {
        HttpHelper.get(url, null, null, Collections.singletonMap("PRIVATE-TOKEN", getPrivateToken()), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, processorMR);
      } catch (Exception ex) {
        throw new PublisherException(String.format("GitLab publisher has failed to connect to %s/%s repository", repository.owner(), repository.repositoryName()), ex);
      }
      
      String urlCommit = getApiUrl() + "/projects/" + processorMR.source_project_id() + "/statuses/" + revision.getRevision();
      postAsync(urlCommit, null, null, message, ContentType.APPLICATION_JSON, Collections.singletonMap("PRIVATE-TOKEN", getPrivateToken()), LogUtil.describe(build));
  
      return;
    }

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

private abstract class JsonResponseProcessor<T> extends DefaultHttpResponseProcessor {

  private final Class<T> myInfoClass;
  private T myInfo;

  JsonResponseProcessor(Class<T> infoClass) {
    myInfoClass = infoClass;
  }

  T getInfo() {
    return myInfo;
  }

  @Override
  public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {

    super.processResponse(response);

    final HttpEntity entity = response.getEntity();
    if (null == entity) {
      throw new HttpPublisherException("GitLab publisher has received no response");
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    entity.writeTo(bos);
    final String json = bos.toString("utf-8");
    myInfo = myGson.fromJson(json, myInfoClass);
  }
}

private class MRInfoResponseProcessor extends JsonResponseProcessor<GitLabMRInfo> {

  private String _id;
  private String _iid;
  private String _source_project_id;
  private String _target_project_id;


  MRInfoResponseProcessor() {
    super(GitLabMRInfo.class);
  }

  String id() {
    return _id;
  }
  String iid() {
    return _iid;
  }
  String source_project_id() {
    return _source_project_id;
  }
  String target_project_id() {
    return _target_project_id;
  }

  @Override
  public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {
    super.processResponse(response);
    GitLabMRInfo userInfo = getInfo();
    if (null == userInfo || null == userInfo.id) {
      throw new HttpPublisherException("GitLab publisher has received a malformed response");
    }
    _id = userInfo.id;
    _iid = userInfo.iid;
    _source_project_id = userInfo.source_project_id;
    _target_project_id = userInfo.target_project_id;
  }
}

}
