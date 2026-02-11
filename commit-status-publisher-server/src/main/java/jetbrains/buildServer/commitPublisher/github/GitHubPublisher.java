

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

package jetbrains.buildServer.commitPublisher.github;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.github.api.GitHubChangeState;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.CommitStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GitHubPublisher extends BaseCommitStatusPublisher {

  private final ChangeStatusUpdater myUpdater;
  private final WebLinks myWebLinks;
  private final StatusPublisherBuildNameProvider myBuildNameProvider;
  private final CommitStatusesCache<CommitStatus> myStatusesCache;

  GitHubPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType,
                  @NotNull String buildFeatureId,
                  @NotNull ChangeStatusUpdater updater,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems,
                  @NotNull WebLinks webLinks,
                  @NotNull StatusPublisherBuildNameProvider buildNameProvider,
                  @NotNull CommitStatusesCache<CommitStatus> commitStatusesCache
  ) {
    super(settings, buildType, buildFeatureId, params, problems);
    myUpdater = updater;
    myWebLinks = webLinks;
    myBuildNameProvider = buildNameProvider;
    myStatusesCache = commitStatusesCache;
  }

  @NotNull
  public String toString() {
    return "github";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return updateQueuedBuildStatus(buildPromotion, revision, additionalTaskInfo, true);
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return updateQueuedBuildStatus(buildPromotion, revision, additionalTaskInfo, false);
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, true);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull final SBuild build, @NotNull final BuildRevision revision, final boolean buildInProgress) throws PublisherException {
    updateBuildStatus(build, revision, buildInProgress);
    return true;
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    CommitStatus commitStatus = getCommitStatus(revision, buildPromotion);
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable CommitStatus commitStatus) {
    if (commitStatus == null) {
      return null;
    }
    Event triggeredEvent = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = isSameBuildType(buildPromotion, commitStatus);
    return new RevisionStatus(triggeredEvent, commitStatus.description, isSameBuildType, getBuildIdFromViewUrl(commitStatus.target_url));
  }

  private boolean isSameBuildType(BuildPromotion buildPromotion, CommitStatus commitStatus) {
    String buildName;
    try {
      buildName = myBuildNameProvider.getBuildName(buildPromotion, myParams);
    } catch (GitHubContextResolveException e) {
      LOG.debug("Context was not resolved for promotion #" + buildPromotion.getId(), e);
      return false;
    }
    return StringUtil.areEqual(buildName, commitStatus.context);
  }

  @Override
  protected WebLinks getLinks() {
    return myWebLinks;
  }

  @Nullable
  private Event getTriggeredEvent(CommitStatus commitStatus) {
    if (commitStatus.state == null) {
      LOG.warn("No GitHub build status is provided. Related event can not be calculated");
      return null;
    }
    GitHubChangeState gitHubChangeState = GitHubChangeState.getByState(commitStatus.state);
    if (gitHubChangeState == null) {
      LOG.warn("Unknown GitHub build status \"" + commitStatus.state + "\". Related event can not be calculated");
      return null;
    }
    String description = commitStatus.description;
    switch (gitHubChangeState) {
      case Pending:
        if (description == null) {
          LOG.info("Can not define exact event for \"Pending\" GitHub build status, because there is no description for status");
          return null;
        }
        if (description.contains(DefaultStatusMessages.BUILD_STARTED)) {
          return Event.STARTED;
        } else if (description.contains(DefaultStatusMessages.BUILD_QUEUED)) {
          return Event.QUEUED;
        }
        LOG.warn("Can not define event for \"Pending\" Github build status and description: \"" + description + "\"");
        break;
      case Success:
      case Error:
        return null;
      case Failure:
        if (description != null && (description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE) || description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED))) {
          return Event.REMOVED_FROM_QUEUE;
        } else {
          return null;
        }
      default:
        LOG.warn("No event is assosiated with GitHub build status \"" + gitHubChangeState + "\". Related event can not be defined");
    }
    return null;
  }

  public String getServerUrl() {
    return myParams.get(Constants.GITHUB_SERVER);
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    Map<String, String> params;
    try {
      params = getParams(build.getBuildPromotion());
    } catch (GitHubContextResolveException e) {
      throw new PublisherException("Can not resolve variables for custom GitHub context", e);
    }
    final ChangeStatusUpdater.Handler h = myUpdater.getHandler(revision.getRoot(), params, this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    String viewUrl = getViewUrl(build.getBuildPromotion());
    if (viewUrl == null) {
      LOG.debug(String.format("Can not build view URL for the build #%d. Probadly build configuration was removed. Status won't be published",
                              build.getBuildId()));
      return;
    }
    if (isStarting) {
      h.changeStarted(revision, build, viewUrl);
    } else {
      h.changeCompleted(revision, build, viewUrl);
    }

    String context = params.get(Constants.BUILD_CUSTOM_NAME);
    myStatusesCache.removeStatusFromCache(revision, context);
  }

  @Nullable
  private CommitStatus getCommitStatus(@NotNull BuildRevision revision, @NotNull BuildPromotion buildPromotion) throws PublisherException {
    Map<String, String> params;
    try {
      params = getParams(buildPromotion);
    } catch (GitHubContextResolveException e) {
      return null;
    }
    ChangeStatusUpdater.Handler handler = myUpdater.getHandler(revision.getRoot(), params, this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to request GitHub status. Please check you have Git VCS roots in the build configuration");
      return null;
    }

    final String context = params.get(Constants.BUILD_CUSTOM_NAME);
    if (context == null) {
      return null;
    }

    AtomicReference<PublisherException> exception = new AtomicReference<>(null);

    CommitStatus statusFromCache = myStatusesCache.getStatusFromCache(revision, context, () -> {
      try {
        return handler.getStatuses(revision);
      } catch (PublisherException e) {
        exception.set(e);
      }
      return Collections.emptyList();
    }, status -> status.context);

    if (exception.get() != null) {
      throw exception.get();
    }
    return statusFromCache;
  }

  private boolean updateQueuedBuildStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision,
                                          @NotNull AdditionalTaskInfo additionalTaskInfo, boolean addingToQueue) throws PublisherException {
    Map<String, String> params;
    try {
      params = getParams(buildPromotion);
    } catch (GitHubContextResolveException e) {
      SBuildType buildType = buildPromotion.getBuildType();
      LOG.debug(String.format("Custom GitHub context for build type \"%s\" contains variables that can be not resolved. Status won't be published",
                              buildType != null ? buildType.getFullName() : buildPromotion.getBuildTypeId()));
      return false;
    }
    final ChangeStatusUpdater.Handler h = myUpdater.getHandler(revision.getRoot(), params, this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return false;
    }
    String viewUrl = getViewUrl(additionalTaskInfo.isPromotionReplaced() ? additionalTaskInfo.getReplacingPromotion() : buildPromotion);
    if (viewUrl == null) {
      LOG.debug(String.format("Can not build view URL for the build #%d. Probadly build configuration was removed. Status won't be published",
                              buildPromotion.getId()));
      return false;
    }
    boolean statusUpdated;
    if (addingToQueue) {
      statusUpdated = h.changeQueued(revision, buildPromotion, additionalTaskInfo, viewUrl);
    } else {
      statusUpdated = h.changeRemovedFromQueue(revision, buildPromotion, additionalTaskInfo, viewUrl);
    }

    if (statusUpdated) {
      String context = params.get(Constants.BUILD_CUSTOM_NAME);
      myStatusesCache.removeStatusFromCache(revision, context);
    }
    return statusUpdated;
  }

  @NotNull
  private Map<String, String> getParams(@NotNull BuildPromotion buildPromotion) throws GitHubContextResolveException, PublisherException {
    String buildName = myBuildNameProvider.getBuildName(buildPromotion, myParams);
    Map<String, String> result = new HashMap<String, String>(myParams);
    result.put(Constants.BUILD_CUSTOM_NAME, buildName);
    return result;
  }
}