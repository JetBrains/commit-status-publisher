/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.gitea;

import com.google.gson.Gson;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaCommitStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GiteaPublisher extends HttpBasedCommitStatusPublisher {

  private final Gson myGson = new Gson();
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  GiteaPublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems,
                 @NotNull WebLinks links) {
    super(settings, buildType, buildFeatureId, params, problems, links);
  }


  @NotNull
  @Override
  public String getId() {
    return Constants.GITEA_PUBLISHER_ID;
  }


  @NotNull
  @Override
  public String toString() {
    return "Gitea";
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    publish(buildPromotion, revision, GiteaBuildStatus.PENDING, additionalTaskInfo);
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    GiteaBuildStatus targetStatus = additionalTaskInfo.isPromotionReplaced() ? GiteaBuildStatus.PENDING : GiteaBuildStatus.WARNING;
    publish(buildPromotion, revision, targetStatus, additionalTaskInfo);
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GiteaBuildStatus.PENDING, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }


  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    GiteaBuildStatus status = build.getBuildStatus().isSuccessful() ? GiteaBuildStatus.SUCCESS : GiteaBuildStatus.FAILURE;
    publish(build, revision, status, build.getStatusDescriptor().getText());
    return true;
  }


  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GiteaBuildStatus.FAILURE, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build, revision, buildInProgress ? GiteaBuildStatus.PENDING : GiteaBuildStatus.SUCCESS, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    return true;
  }


  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GiteaBuildStatus.WARNING, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    GiteaCommitStatus commitStatus = getLatestCommitStatusForBuild(revision, removedBuild.getBuildType());
    return getRevisionStatusForRemovedBuild(removedBuild, commitStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable GiteaCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(myLinks.getQueuedBuildUrl(removedBuild), commitStatus.target_url);
    return new RevisionStatus(event, commitStatus.description, isSameBuild);
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    GiteaCommitStatus commitStatus = getLatestCommitStatusForBuild(revision, buildPromotion.getBuildType());
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  private GiteaCommitStatus getLatestCommitStatusForBuild(@NotNull BuildRevision revision, @Nullable SBuildType buildType) throws PublisherException {
    String url = buildRevisionStatusesUrl(revision, buildType);
    url += "?access_token=" + getPrivateToken();
    ResponseEntityProcessor<GiteaCommitStatus[]> processor = new ResponseEntityProcessor<>(GiteaCommitStatus[].class);
    GiteaCommitStatus[] commitStatuses = get(url, null, null, null, processor);
    if (commitStatuses == null || commitStatuses.length == 0) {
      return null;
    }
    return commitStatuses[0];
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable GiteaCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(getViewUrl(buildPromotion), commitStatus.target_url);
    return new RevisionStatus(event, commitStatus.description, isSameBuild);
  }

  private String buildRevisionStatusesUrl(@NotNull BuildRevision revision, @Nullable BuildType buildType) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Gitea API URL parameter");
    String pathPrefix = GiteaSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    return GiteaSettings.getProjectsUrl(getApiUrl(), repository.owner(), repository.repositoryName()) + "/statuses/" + revision.getRevision();
  }

  private Event getTriggeredEvent(GiteaCommitStatus commitStatus) {
    if (commitStatus.status == null) {
      LOG.warn("No Gitea build status is provided. Related event can not be calculated");
      return null;
    }
    GiteaBuildStatus status = GiteaBuildStatus.getByName(commitStatus.status);
    if (status == null) {
      LOG.warn("Unknown Gitea build status \"" + commitStatus.status + "\". Related event can not be calculated");
      return null;
    }

    switch (status) {
      case WARNING:
        return Event.INTERRUPTED;
      case PENDING:
        if (commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)) {
          return Event.REMOVED_FROM_QUEUE;
        }
        if (commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_QUEUED)) {
          return Event.QUEUED;
        }
      case SUCCESS:
      case FAILURE:
      case ERROR:
        return null;
      default:
        LOG.warn("No event is assosiated with Gitea build status \"" + status + "\". Related event can not be defined");
    }
    return null;
  }


  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull GiteaBuildStatus status,
                       @NotNull String description) throws PublisherException {
    SBuildType buildType = build.getBuildType();
    String buildName = buildType != null ? buildType.getFullName() : build.getBuildTypeExternalId();
    String message = createMessage(status, buildName, revision, getViewUrl(build), description);
    publish(message, revision, LogUtil.describe(build));
  }

  private void publish(@NotNull BuildPromotion buildPromotion,
                       @NotNull BuildRevision revision,
                       @NotNull GiteaBuildStatus status,
                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String url = getViewUrl(buildPromotion);
    String description = additionalTaskInfo.getComment();
    SBuildType buildType = buildPromotion.getBuildType();
    String buildName = buildType != null ? buildType.getFullName() : buildPromotion.getBuildTypeExternalId();
    String message = createMessage(status, buildName, revision, url, description);
    publish(message, revision, LogUtil.describe(buildPromotion));
  }

  private void publish(@NotNull String message,
                       @NotNull BuildRevision revision,
                       @NotNull String buildDescription) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Gitea API URL parameter");
    String pathPrefix = GiteaSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());

    try {
      publish(revision.getRevision(), message, repository, buildDescription);
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to Gitea for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  private void publish(@NotNull String commit, @NotNull String data, @NotNull Repository repository, @NotNull String buildDescription) {
    String url = GiteaSettings.getProjectsUrl(getApiUrl(), repository.owner(), repository.repositoryName()) + "/statuses/" + commit;
    LOG.debug("Request url: " + url + ", message: " + data);
    url += "?access_token=" + getPrivateToken();
    postJson(url, null, null, data, null, buildDescription);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400) {
      String responseString = response.getContent();
      if (!responseString.contains("Cannot transition status via :enqueue from :pending") &&
          !responseString.contains("Cannot transition status via :enqueue from :running") &&
          !responseString.contains("Cannot transition status via :run from :running")) {
        throw new HttpPublisherException(statusCode,
          response.getStatusText(), "HTTP response error: " + responseString);
      }
    }
  }

  @NotNull
  private String createMessage(@NotNull GiteaBuildStatus status,
                               @NotNull String name,
                               @NotNull BuildRevision revision,
                               @NotNull String url,
                               @NotNull String description) {

    final Map<String, String> data = new LinkedHashMap<>();
    data.put("state", status.getName());
    data.put("context", name);
    data.put("description", description);
    data.put("target_url", url);
    return myGson.toJson(data);
  }

  @Nullable
  static Repository parseRepository(@NotNull VcsRoot root, @Nullable String pathPrefix) {
    if ("jetbrains.git".equals(root.getVcsName())) {
      String url = root.getProperty("url");
      return url == null ? null : VCS_URL_PARSER.parseRepositoryUrl(url, pathPrefix);
    } else {
      return null;
    }
  }


  private String getApiUrl() {
    return HttpHelper.stripTrailingSlash(myParams.get(Constants.GITEA_API_URL));
  }

  private String getPrivateToken() {
    return myParams.get(Constants.GITEA_TOKEN);
  }

}
