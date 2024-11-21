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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaCommitStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GiteaPublisher extends HttpBasedCommitStatusPublisher<GiteaBuildStatus> {

  private static final Gson myGson = new Gson();
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  @NotNull private final CommitStatusesCache<GiteaCommitStatus> myStatusesCache;

  GiteaPublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems,
                 @NotNull WebLinks links,
                 @NotNull CommitStatusesCache<GiteaCommitStatus> statusesCache) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    myStatusesCache = statusesCache;
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
    publish(buildPromotion, revision, GiteaBuildStatus.FAILURE, additionalTaskInfo);
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
    publish(build, revision, GiteaBuildStatus.FAILURE, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    SBuildType buildType = removedBuild.getBuildType();
    GiteaCommitStatus commitStatus = getLatestCommitStatusForBuild(revision, buildType.getFullName(), removedBuild.getBuildPromotion());
    return getRevisionStatusForRemovedBuild(removedBuild, commitStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable GiteaCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(getBuildName(removedBuild.getBuildPromotion()), commitStatus.context);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType, getBuildIdFromViewUrl(commitStatus.target_url));
  }

  private String getBuildName(BuildPromotion promotion) {
    SBuildType buildType = promotion.getBuildType();
    return buildType != null ? buildType.getFullName() : promotion.getBuildTypeExternalId();
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    SBuildType buildType = buildPromotion.getBuildType();
    GiteaCommitStatus commitStatus = getLatestCommitStatusForBuild(revision, buildType == null ? buildPromotion.getBuildTypeExternalId() : buildType.getFullName(), buildPromotion);
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  private GiteaCommitStatus getLatestCommitStatusForBuild(@NotNull BuildRevision revision, @NotNull String buildName, @NotNull BuildPromotion promotion) throws PublisherException {
  AtomicReference<PublisherException> exception = new AtomicReference<>(null);
  GiteaCommitStatus statusFromCache = myStatusesCache.getStatusFromCache(revision, buildName, () -> {
    SBuildType exactBuildTypeToLoadStatuses = promotion.isPartOfBuildChain() ? null : promotion.getBuildType();
    try {
      GiteaCommitStatus[] commitStatuses = loadGiteaStatuses(revision, exactBuildTypeToLoadStatuses);
      return Arrays.asList(commitStatuses);
    } catch (PublisherException e) {
      exception.set(e);
      return Collections.emptyList();
    }
  }, status -> status.context);

  if (exception.get() != null) {
    throw exception.get();
  }

  return statusFromCache;
}

  private GiteaCommitStatus[] loadGiteaStatuses(@NotNull BuildRevision revision, @Nullable SBuildType buildType) throws PublisherException {
    String url = buildRevisionStatusesUrl(revision, buildType);
    url += "?access_token=" + getPrivateToken();
    ResponseEntityProcessor<GiteaCommitStatus[]> processor = new ResponseEntityProcessor<>(GiteaCommitStatus[].class);
    GiteaCommitStatus[] commitStatuses = get(url, null, null, processor);
    if (commitStatuses == null || commitStatuses.length == 0) {
      return new GiteaCommitStatus[0];
    }
    return commitStatuses;
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable GiteaCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(getBuildName(buildPromotion), commitStatus.context);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType, getBuildIdFromViewUrl(commitStatus.target_url));
  }

  private String buildRevisionStatusesUrl(@NotNull BuildRevision revision, @Nullable BuildType buildType) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.isEmpty())
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
      case PENDING:
        if (commitStatus.description == null || commitStatus.description.contains(DefaultStatusMessages.BUILD_QUEUED)) {
          return Event.QUEUED;
        }
        if (commitStatus.description.contains(DefaultStatusMessages.BUILD_STARTED)) {
          return Event.STARTED;
        }
        if (commitStatus.description.contains(DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL)) {
          return null;
        }
      case SUCCESS:
      case ERROR:
        return null;
      case FAILURE:
        return commitStatus.description != null
          && (commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)
          || commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED))
          ? Event.REMOVED_FROM_QUEUE : null;
      default:
        LOG.warn("No event is assosiated with Gitea build status \"" + status + "\". Related event can not be defined");
    }
    return null;
  }


  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull GiteaBuildStatus status,
                       @NotNull String description) throws PublisherException {
    String buildName = getBuildName(build.getBuildPromotion());
    String message = createMessage(status, buildName, revision, getViewUrl(build), description);
    publish(message, revision, LogUtil.describe(build));
    myStatusesCache.removeStatusFromCache(revision, buildName);
  }

  private void publish(@NotNull BuildPromotion buildPromotion,
                       @NotNull BuildRevision revision,
                       @NotNull GiteaBuildStatus status,
                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String url = getViewUrl(buildPromotion);
    if (url == null) {
      LOG.debug(String.format("Can not build view URL for the build #%d. Probably build configuration was removed. Status \"%s\" won't be published",
        buildPromotion.getId(), status.getName()));
      return;
    }
    String description = additionalTaskInfo.getComment();
    String buildName = getBuildName(buildPromotion);
    String message = createMessage(status, buildName, revision, url, description);
    publish(message, revision, LogUtil.describe(buildPromotion));
    myStatusesCache.removeStatusFromCache(revision, buildName);
  }

  private void publish(@NotNull String message,
                       @NotNull BuildRevision revision,
                       @NotNull String buildDescription) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.isEmpty())
      throw new PublisherException("Missing Gitea API URL parameter");
    String pathPrefix = GiteaSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());

    try {
      publish(revision.getRevision(), message, repository, buildDescription);
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to Gitea(" + apiUrl + ") for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  private void publish(@NotNull String commit,
                       @NotNull String data,
                       @NotNull Repository repository,
                       @NotNull String buildDescription) throws PublisherException {
    String url = GiteaSettings.getProjectsUrl(getApiUrl(), repository.owner(), repository.repositoryName()) + "/statuses/" + commit;
    LOG.debug("Request url: " + url + ", message: " + data);
    url += "?access_token=" + getPrivateToken();
    postJson(url, null, data, null, buildDescription);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400) {
      String responseString = response.getContent();
      if (404 == statusCode) {
        throw new HttpPublisherException(statusCode, "Repository not found. Please check if it was renamed or moved to another namespace");
      } else if (!responseString.contains("Cannot transition status via :enqueue from :pending") &&
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

  /* Currently not needed
   * determineStatusCommit
   * getMergeRequest
   * getParentRevisions
   * determineParentInSourceBranch
   * isOnlyInSourceBranch
   * supportMergeResults
   */
}
