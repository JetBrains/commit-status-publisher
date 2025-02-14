

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.space;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.space.data.SpaceBuildStatusInfo;
import jetbrains.buildServer.commitPublisher.space.data.SpaceBuildStatusInfoPayload;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class SpacePublisher extends HttpBasedCommitStatusPublisher<SpaceBuildStatus> {
  private static final String UNKNOWN_BUILD_CONFIGURATION = "Unknown build configuration";
  private static final String UNKNWON_GIT_SHA = "0000000000000000000000000000000000000000";

  private final SpaceConnectDescriber mySpaceConnector;
  private final Gson myGson = new Gson();
  private final CommitStatusesCache<SpaceBuildStatusInfo> myStatusesCache;
  private final boolean myHasBuildFeature;

  SpacePublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType,
                 @NotNull String buildFeatureId,
                 @NotNull WebLinks links,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems,
                 @NotNull SpaceConnectDescriber spaceConnector,
                 @NotNull CommitStatusesCache<SpaceBuildStatusInfo> statusesCache,
                 boolean hasBuildFeature) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    mySpaceConnector = spaceConnector;
    myStatusesCache = statusesCache;
    myHasBuildFeature = hasBuildFeature;
  }

  @NotNull
  @Override
  public String toString() {
    return "space";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.SPACE_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion,
                             @NotNull BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return publishQueued(buildPromotion, revision, SpaceBuildStatus.SCHEDULED, additionalTaskInfo);
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion,
                                       @NotNull BuildRevision revision,
                                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    if (additionalTaskInfo.commentContains(DefaultStatusMessages.BUILD_STARTED)) {
      return false;
    }
    return publishQueued(buildPromotion, revision, SpaceBuildStatus.TERMINATED, additionalTaskInfo);
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, SpaceBuildStatus.RUNNING, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    SpaceBuildStatus status = build.getBuildStatus().isSuccessful() ? SpaceBuildStatus.SUCCEEDED : SpaceBuildStatus.FAILED;
    publish(build, revision, status, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, SpaceBuildStatus.TERMINATED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, SpaceBuildStatus.FAILING, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build, revision, buildInProgress ? SpaceBuildStatus.RUNNING : SpaceBuildStatus.SUCCEEDED, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    return true;
  }

  @Nullable
  private Long getBuildId(@NotNull SpaceBuildStatusInfo buildStatus) {
    Long buildId = NumberUtils.toLong(buildStatus.taskBuildId, -1);
    return buildId > -1 ? buildId : getBuildIdFromViewUrl(buildStatus.url);
  }

  private String getTaskId(BuildPromotion buildPromotion) {
    return buildPromotion.getBuildTypeExternalId();
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    SpaceBuildStatusInfo buildStatus = getExternalCheckStatus(revision, buildPromotion.getBuildType());
    return getRevisionStatus(buildPromotion, buildStatus);
  }

  private SpaceBuildStatusInfo getExternalCheckStatus(@NotNull BuildRevision revision, @Nullable SBuildType buildType) throws PublisherException {
    String buildFullName = buildType != null ? buildType.getFullName() : UNKNOWN_BUILD_CONFIGURATION;
    AtomicReference<PublisherException> exception = new AtomicReference<>(null);
    SpaceBuildStatusInfo status = myStatusesCache.getStatusFromCache(revision, buildFullName, () -> {
      ResponseEntityProcessor<SpaceBuildStatusInfo[]> processor = new ResponseEntityProcessor<>(SpaceBuildStatusInfo[].class);
      final SpaceToken token;
      try {
        token = requestToken(revision.getRoot().getName(), buildFullName);
      } catch (PublisherException e) {
        exception.set(e);
        return Collections.emptyList();
      }
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
      token.toHeader(headers);
      SpaceBuildStatusInfo[] commitStatuses;
      try {
        String url = buildStatusesUrl(revision);
        commitStatuses = get(url, null, headers, processor);
      } catch (PublisherException e) {
        exception.set(e);
        return Collections.emptyList();
      }
      if (commitStatuses == null || commitStatuses.length == 0) {
        return Collections.emptyList();
      }
      return Arrays.asList(commitStatuses);
    }, spaceStatus -> spaceStatus.taskName);

    if (exception.get() != null) {
      throw exception.get();
    }

    return status;
  }

  private String buildStatusesUrl(BuildRevision revision) throws PublisherException {
    Repository repo = SpaceUtils.getRepositoryInfo(revision.getRoot(), myParams.get(Constants.SPACE_PROJECT_KEY));
    return SpaceApiUrls.commitStatusUrl(mySpaceConnector.getFullAddress(), repo.owner(), repo.repositoryName(), revision.getRevision());
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable SpaceBuildStatusInfo commitStatus) {
    if (commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(getTaskId(buildPromotion), commitStatus.taskId);
    return new RevisionStatus(event, commitStatus.description, isSameBuild, getBuildId(commitStatus));
  }

  private Event getTriggeredEvent(SpaceBuildStatusInfo commitStatus) {
    if (commitStatus.executionStatus == null) {
      LOG.warn("No Space build status is provided. Related event can not be calculated");
      return null;
    }
    SpaceBuildStatus status = SpaceBuildStatus.getByName(commitStatus.executionStatus);
    if (status == null) {
      LOG.warn(String.format("Unknown Space build status: \"%s\". Related event can not be calculated", commitStatus.executionStatus));
      return null;
    }
    switch (status) {
      case SCHEDULED:
        return Event.QUEUED;
      case RUNNING:
        if (commitStatus.description == null) return null;
        return commitStatus.description.contains(DefaultStatusMessages.BUILD_STARTED) ? Event.STARTED : null;
      case SUCCEEDED:
      case FAILED:
      case FAILING:
        return null;
      case TERMINATED:
        return commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE) ? Event.REMOVED_FROM_QUEUE :
               commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED) ? Event.REMOVED_FROM_QUEUE :
               null;
      default:
        LOG.warn("No event is assosiated with Space build status \"" + commitStatus.executionStatus + "\". Related event can not be defined");
    }
    return null;
  }

  private boolean publishQueued(@NotNull BuildPromotion buildPromotion,
                                @NotNull BuildRevision revision,
                                @NotNull SpaceBuildStatus status,
                                @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    List<String> changes = buildPromotion.getContainingChanges().stream()
      .limit(200)
      .map(VcsModification::getVersion)
      .collect(Collectors.toList());
    String viewUrl = getViewUrl(buildPromotion);
    if (viewUrl == null) {
      LOG.warn(String.format("Can not build view URL for the build #%d. Probadly build configuration was removed. Status \"%s\" won't be published",
                              buildPromotion.getId(), status.getName()));
      return false;
    }
    Date timestamp = buildPromotion.getServerStartDate() != null ? buildPromotion.getServerStartDate() : buildPromotion.getQueuedDate();
    String taskName = buildPromotion.getBuildType() != null ? buildPromotion.getBuildType().getFullName() : UNKNOWN_BUILD_CONFIGURATION;
    final String payload = createPayload(
      changes,
      status,
      viewUrl,
      SpaceSettings.getDisplayName(myParams),
      taskName,
      getTaskId(buildPromotion),
      buildPromotion.getId(),
      (timestamp == null ? new Date() : timestamp).getTime(),
      additionalTaskInfo.getComment()
    );

    String description = LogUtil.describe(buildPromotion);
    final SpaceToken token = requestToken(revision.getRoot().getName(), description);

    final Repository repoInfo = SpaceUtils.getRepositoryInfo(revision.getRoot(), myParams.get(Constants.SPACE_PROJECT_KEY));

    final String requestUrl = SpaceApiUrls.commitStatusUrl(
      mySpaceConnector.getFullAddress(),
      repoInfo.owner(),
      repoInfo.repositoryName(),
      revision.getRevision()
    );

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(HttpHeaders.ACCEPT, ContentType.TEXT_PLAIN.getMimeType());
    token.toHeader(headers);

    postJson(requestUrl, null, payload, headers, description);
    myStatusesCache.removeStatusFromCache(revision, taskName);
    return true;
  }

  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull SpaceBuildStatus status,
                       @NotNull String description) throws PublisherException {
    Date finishDate = build.getFinishDate();
    List<String> changes = build.getContainingChanges()
      .stream()
      .limit(200)
      .map(VcsModification::getVersion)
      .collect(Collectors.toList());

    String payload = createPayload(
      changes,
      status,
      getViewUrl(build),
      SpaceSettings.getDisplayName(myParams),
      build.getFullName(),
      getTaskId(build.getBuildPromotion()),
      build.getBuildId(),
      (finishDate == null ? build.getServerStartDate() : finishDate).getTime(),
      description
    );

    String buildDescription = LogUtil.describe(build);
    SpaceToken token = requestToken(revision.getRoot().getName(), buildDescription);

    Repository repoInfo= SpaceUtils.getRepositoryInfo(revision.getRoot(), myParams.get(Constants.SPACE_PROJECT_KEY));

    String url = SpaceApiUrls.commitStatusUrl(
      mySpaceConnector.getFullAddress(),
      repoInfo.owner(),
      repoInfo.repositoryName(),
      revision.getRevision()
    );

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(HttpHeaders.ACCEPT, ContentType.TEXT_PLAIN.getMimeType());
    token.toHeader(headers);

    postJson(url, null, payload, headers, buildDescription);
    myStatusesCache.removeStatusFromCache(revision, build.getFullName());
  }

  @NotNull
  private SpaceToken requestToken(String vcsRootName, String description) throws PublisherException {
    try {
      return SpaceToken.requestToken(
        mySpaceConnector.getServiceId(),
        mySpaceConnector.getServiceSecret(),
        mySpaceConnector.getFullAddress(),
        getConnectionTimeout(),
        myGson,
        getSettings().trustStore()
      );
    } catch (Exception e) {
      PublisherException ex = new PublisherException("Commit Status Publisher has failed to obtain a token from JetBrains Space. " + e, e);
      RetryResponseProcessor.processNetworkException(e, ex);
      throw ex;
    }
  }

  @NotNull
  private String createPayload(@NotNull List<String> changes,
                               @NotNull SpaceBuildStatus executionStatus,
                               @NotNull String url,
                               @NotNull String externalServiceName,
                               @NotNull String taskName,
                               @NotNull String taskId,
                               long taskBuildId,
                               @Nullable Long timestamp,
                               @Nullable String description) {
    final String effectiveTaskBuildId = TeamCityProperties.getBooleanOrTrue("teamcity.commitStatusPublisher.space.publishBuildId") ?
                                        String.valueOf(taskBuildId) : null;
    SpaceBuildStatusInfoPayload statusInfoPayload = new SpaceBuildStatusInfoPayload(changes, executionStatus.getName(), description,
                                                                                    timestamp, taskName, url, taskId, externalServiceName,
                                                                                    effectiveTaskBuildId);
    return myGson.toJson(statusInfoPayload);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    int statusCode = response.getStatusCode();
    String responseContent = response.getContent();

    if (statusCode >= 400) {
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error: " + (responseContent != null ? responseContent : "<empty>"));
    }
  }

  /**
   * Returns the <em>"unknown git SHA"</em> revision.
   * This revision can serve as a fallback.
   * Space is able to match safe-merge statuses by build ID.
   * See: TW-84882
   *
   * @param build the current build
   * @return singleton collection of an artificial build revision pointing to {@link #UNKNWON_GIT_SHA}
   */
  @NotNull
  @Override
  public Collection<BuildRevision> getFallbackRevisions(@Nullable SBuild build) {
    if (build == null) {
      return super.getFallbackRevisions(null);
    }

    final String vcsRootId = getVcsRootId();
    final Optional<VcsRootInstance> maybeVcsRootInstance = build.getVcsRootEntries()
                                                                .stream()
                                                                .map(VcsRootInstanceEntry::getVcsRoot)
                                                                .filter(
                                                                  vcsRootInstance -> (vcsRootId != null && vcsRootIdMatches(vcsRootId, (SVcsRootEx)vcsRootInstance.getParent())) ||
                                                                                     getSettings().isPublishingForVcsRoot(vcsRootInstance))
                                                                .findAny();

    if (!maybeVcsRootInstance.isPresent()) {
      LOG.warn("Unable to construct fallback build revision for Space build " + LogUtil.describe(build) + ": no suitable VCS root instance found");
      return super.getFallbackRevisions(build);
    }
    return Collections.singletonList(new BuildRevision(maybeVcsRootInstance.get(), UNKNWON_GIT_SHA, "", UNKNWON_GIT_SHA));
  }

  private static boolean vcsRootIdMatches(@NotNull String vcsRootId, @NotNull SVcsRootEx root) {
    return (vcsRootId.equals(root.getExternalId()) || root.isAliasExternalId(vcsRootId) || vcsRootId.equals(String.valueOf(root.getId())));
  }

  @Override
  public boolean hasBuildFeature() {
    return myHasBuildFeature;
  }
}