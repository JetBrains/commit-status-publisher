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

package jetbrains.buildServer.commitPublisher.space;

import com.google.gson.Gson;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.space.data.SpaceBuildStatusInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsModification;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class SpacePublisher extends HttpBasedCommitStatusPublisher {

  private static final String UNKNOWN_BUILD_CONFIGURATION = "Unknown build configuration";

  private final SpaceConnectDescriber mySpaceConnector;
  private final Gson myGson = new Gson();
  private final CustomDataStorageManager myDataStorageManager;

  SpacePublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull WebLinks links,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems,
                 @NotNull SpaceConnectDescriber spaceConnector,
                 @NotNull CustomDataStorageManager dataStorageManager) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    mySpaceConnector = spaceConnector;
    myDataStorageManager = dataStorageManager;
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
    additionalTaskInfo.appendCommentTo(DefaultStatusMessages.BUILD_QUEUED);
    return publishQueued(buildPromotion, revision, SpaceBuildStatus.SCHEDULED, additionalTaskInfo);
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion,
                                       @NotNull BuildRevision revision,
                                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    if (additionalTaskInfo.commentContains(DefaultStatusMessages.BUILD_STARTED)) {
      return false;
    }
    SpaceBuildStatus targetStatus = additionalTaskInfo.isPromotionReplaced() ? SpaceBuildStatus.SCHEDULED : SpaceBuildStatus.TERMINATED;
    return publishQueued(buildPromotion, revision, targetStatus, additionalTaskInfo);
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

  @Override
  public boolean publish(BuildRevision revision, CommonBuildStatus status) throws PublisherException {
    List<String> changes = status.getAttribute(SpaceSettings.CHANGES_FIELD);
    Long timestamp = status.getAttribute(SpaceSettings.TIMESTAMP_FIELD);
    SpaceBuildStatusInfo statusInfo = new SpaceBuildStatusInfo(changes, status.getState(), status.getDescription(), timestamp,
                                                               status.getAttribute(SpaceSettings.TASK_ID_FIELD), status.getBuild(), status.getUrl(),
                                                               status.getAttribute(SpaceSettings.EXTERNAL_SERVICE_NAME_FIELD));
    publish(revision, statusInfo, "Build: " + status.getBuild());
    return true;
  }

  @Override
  public CommonBuildStatus getLatestInformativeBuildStatusForPromotion(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) {
    Optional<SpaceBuildStatusInfo> status = getBuildStatusFromStorage(buildPromotion.getBuildType().getFullName(), revision,
                                                                      str -> myGson.fromJson(str, SpaceBuildStatusInfo.class),
                                                                      new SpaceInformativeCommitStatusFilter(buildPromotion));
    if (status.isPresent()) {
      SpaceBuildStatusInfo spaceStatus = status.get();
      return convertToCommonStatus(spaceStatus);
    }
    return null;
  }

  private CommonBuildStatus convertToCommonStatus(SpaceBuildStatusInfo spaceStatus) {
    Map<String, Object> attributes = new HashMap<>();
    if (spaceStatus.changes != null) {
      attributes.put(SpaceSettings.CHANGES_FIELD, spaceStatus.changes);
    }
    if (spaceStatus.timestamp != null) {
      attributes.put(SpaceSettings.TIMESTAMP_FIELD, spaceStatus.timestamp);
    }
    if (spaceStatus.taskId != null) {
      attributes.put(SpaceSettings.TASK_ID_FIELD, spaceStatus.taskId);
    }
    if (spaceStatus.externalServiceName != null) {
      attributes.put(SpaceSettings.EXTERNAL_SERVICE_NAME_FIELD, spaceStatus.externalServiceName);
    }
    return new CommonBuildStatus(spaceStatus.taskName, spaceStatus.executionStatus, spaceStatus.description, spaceStatus.url, attributes);
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    SpaceBuildStatusInfo buildStatus = getExternalCheckStatus(revision, removedBuild.getBuildType());
    return getRevisionStatusForRemovedBuild(removedBuild, buildStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable SpaceBuildStatusInfo buildStatus) {
    if (buildStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(buildStatus);
    boolean isSameBuild = StringUtil.areEqual(myLinks.getQueuedBuildUrl(removedBuild), buildStatus.url);
    return new RevisionStatus(event, isSameBuild);
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    SpaceBuildStatusInfo buildStatus = getExternalCheckStatus(revision, buildPromotion.getBuildType());
    return getRevisionStatus(buildPromotion, buildStatus);
  }

  private SpaceBuildStatusInfo getExternalCheckStatus(@NotNull BuildRevision revision, @Nullable SBuildType buildType) throws PublisherException {
    Repository repo = SpaceUtils.getRepositoryInfo(revision.getRoot(), myParams.get(Constants.SPACE_PROJECT_KEY));
    String url = SpaceApiUrls.commitStatusUrl(mySpaceConnector.getFullAddress(), repo.owner(), repo.repositoryName(), revision.getRevision());
    ResponseEntityProcessor<SpaceBuildStatusInfo[]> processor = new ResponseEntityProcessor<>(SpaceBuildStatusInfo[].class);
    String buildFullName = buildType != null ? buildType.getFullName() : UNKNOWN_BUILD_CONFIGURATION;
    final SpaceToken token = requestToken(revision.getRoot().getName(), buildFullName);
    if (token == null) {
      return null;
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
    token.toHeader(headers);
    SpaceBuildStatusInfo[] commitStatuses = get(url, null, null, headers, processor);
    if (commitStatuses == null || commitStatuses.length == 0) {
      return null;
    }
    Optional<SpaceBuildStatusInfo> commitStatusOpt = Arrays.stream(commitStatuses)
                                                           .filter(status -> buildFullName.equals(status.taskName))
                                                           .findAny();
    return commitStatusOpt.isPresent() ? commitStatusOpt.get() : null;
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable SpaceBuildStatusInfo commitStatus) {
    if (commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(getViewUrl(buildPromotion), commitStatus.url);
    return new RevisionStatus(event, isSameBuild);
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
        if (commitStatus.description == null) return Event.QUEUED;
        return commitStatus.description.contains(DefaultStatusMessages.BUILD_QUEUED) ? Event.QUEUED : Event.REMOVED_FROM_QUEUE;
      case RUNNING:
      case SUCCEEDED:
      case FAILED:
      case FAILING:
      case TERMINATED:
        return null;  // these statuses do not affect on further behaviour
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
    Date timestamp = buildPromotion.getServerStartDate() != null ? buildPromotion.getServerStartDate() : buildPromotion.getQueuedDate();
    SpaceBuildStatusInfo statusInfo = new SpaceBuildStatusInfo(changes, status.getName(), additionalTaskInfo.compileQueueRelatedMessage(),
                                                               (timestamp == null ? new Date() : timestamp).getTime(), buildPromotion.getBuildTypeExternalId(),
                                                               buildPromotion.getBuildType() != null ? buildPromotion.getBuildType().getFullName() : UNKNOWN_BUILD_CONFIGURATION,
                                                               viewUrl, SpaceSettings.getDisplayName(myParams));

    return publish(revision, statusInfo, LogUtil.describe(buildPromotion));
  }

  private boolean publish(@NotNull BuildRevision revision, @NotNull SpaceBuildStatusInfo statusInfo, @NotNull String buildDescription) throws PublisherException {
    final SpaceToken token = requestToken(revision.getRoot().getName(), buildDescription);
    if (token == null) {
      return false;
    }

    final String payload = myGson.toJson(statusInfo);
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

    postJson(requestUrl, null, null, payload, headers, buildDescription);
    storeStatusInHistory(statusInfo.taskName, revision.getRevision(), payload);
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
    String buildDescription = LogUtil.describe(build);
    SpaceBuildStatusInfo statusInfo = new SpaceBuildStatusInfo(changes, status.getName(), description, (finishDate == null ? build.getServerStartDate() : finishDate).getTime(),
                                                               build.getBuildTypeExternalId(), build.getFullName(), myLinks.getViewResultsUrl(build),
                                                               SpaceSettings.getDisplayName(myParams));
    publish(revision, statusInfo, buildDescription);
  }

  @Nullable
  private SpaceToken requestToken(String vcsRootName, String description) {
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
      myProblems.reportProblem("Commit Status Publisher has failed to obtain a token from JetBrains Space for VCS root " + vcsRootName,
                               this, description, null, e, LOG);
      return null;
    }
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    int statusCode = response.getStatusCode();
    String responseContent = response.getContent();

    if (statusCode >= 400) {
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error: " + (responseContent != null ? responseContent : "<empty>"));
    }
  }

  @Override
  protected CustomDataStorage getStatusHistoryDataStorage() {
    return myDataStorageManager.getCustomDataStorage(getClass());
  }

  private class SpaceInformativeCommitStatusFilter extends InformativeCommitStatusFilter<SpaceBuildStatusInfo> {

    public SpaceInformativeCommitStatusFilter(BuildPromotion buildPromotion) {
      super(getPossibleViewUrls(buildPromotion));
    }

    @Override
    protected String getUrl(SpaceBuildStatusInfo status) {
      return status.url;
    }

    @Override
    protected String getDescription(SpaceBuildStatusInfo status) {
      return status.description;
    }
  }
}
