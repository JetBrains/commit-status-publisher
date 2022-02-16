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

package jetbrains.buildServer.commitPublisher.github;

import java.util.HashMap;
import java.util.Map;
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

  GitHubPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                  @NotNull ChangeStatusUpdater updater,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems,
                  @NotNull WebLinks webLinks) {
    super(settings, buildType, buildFeatureId, params, problems);
    myUpdater = updater;
    myWebLinks = webLinks;
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
    boolean isSameBuild = StringUtil.areEqual(getViewUrl(buildPromotion), commitStatus.target_url);
    return new RevisionStatus(triggeredEvent, commitStatus.description, isSameBuild);
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    CommitStatus commitStatus = getCommitStatus(revision, removedBuild.getBuildPromotion());
    return getRevisionStatusForRemovedBuild(removedBuild, commitStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable CommitStatus commitStatus) {
    if (commitStatus == null) {
      return null;
    }
    Event triggeredEvent = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(myWebLinks.getQueuedBuildUrl(removedBuild), commitStatus.target_url);
    return new RevisionStatus(triggeredEvent, commitStatus.description, isSameBuild);
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
    switch (gitHubChangeState) {
      case Pending:
        String description = commitStatus.description;
        if (description == null) {
          LOG.info("Can not define exact event for \"Pending\" GitHub build status, because there is no description for status");
          return null;
        }
        if (description.contains(DefaultStatusMessages.BUILD_STARTED)) {
          return null;
        } else if (description.contains(DefaultStatusMessages.BUILD_QUEUED)) {
          return Event.QUEUED;
        } else if (description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)) {
          return Event.REMOVED_FROM_QUEUE;
        }
        LOG.warn("Can not define event for \"Pending\" Github build status and description: \"" + description + "\"");
        break;
      case Success:
      case Error:
      case Failure:
        return null;  // these statuses do not affect on further behaviour
      default:
        LOG.warn("No event is assosiated with GitHub build status \"" + gitHubChangeState + "\". Related event can not be defined");
    }
    return null;
  }

  public String getServerUrl() {
    return myParams.get(Constants.GITHUB_SERVER);
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final ChangeStatusUpdater.Handler h = myUpdater.getHandler(revision.getRoot(), getParams(build.getBuildPromotion()), this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    String viewUrl = getViewUrl(build.getBuildPromotion());
    if (isStarting) {
      h.changeStarted(revision, build, viewUrl);
    } else {
      h.changeCompleted(revision, build, viewUrl);
    }
  }

  @Nullable
  private CommitStatus getCommitStatus(@NotNull BuildRevision revision, @NotNull BuildPromotion buildPromotion) throws PublisherException {
    ChangeStatusUpdater.Handler handler = myUpdater.getHandler(revision.getRoot(), getParams(buildPromotion), this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to request GitHub status. Please check you have Git VCS roots in the build configuration");
      return null;
    }
    return handler.getStatus(revision);
  }

  private boolean updateQueuedBuildStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision,
                                          @NotNull AdditionalTaskInfo additionalTaskInfo, boolean addingToQueue) throws PublisherException {
    final ChangeStatusUpdater.Handler h = myUpdater.getHandler(revision.getRoot(), getParams(buildPromotion), this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return false;
    }
    String viewUrl = getViewUrl(additionalTaskInfo.isPromotionReplaced() ? additionalTaskInfo.getReplacingPromotion() : buildPromotion);
    if (addingToQueue) {
      return h.changeQueued(revision, buildPromotion, additionalTaskInfo, viewUrl);
    } else {
      return h.changeRemovedFromQueue(revision, buildPromotion, additionalTaskInfo, viewUrl);
    }
  }

  @NotNull
  private String getViewUrl(BuildPromotion buildPromotion) {
    SBuild associatedBuild = buildPromotion.getAssociatedBuild();
    if (associatedBuild != null) {
      return myWebLinks.getViewResultsUrl(associatedBuild);
    }
    SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild != null) {
      return myWebLinks.getQueuedBuildUrl(queuedBuild);
    }
    return buildPromotion.getBuildType() != null ?
           myWebLinks.getConfigurationHomePageUrl(buildPromotion.getBuildType()) :
           myWebLinks.getRootUrlByProjectExternalId(buildPromotion.getProjectExternalId());
  }

  @NotNull
  private Map<String, String> getParams(@NotNull BuildPromotion buildPromotion) {
    String context = getBuildContext(buildPromotion);
    Map<String, String> result = new HashMap<String, String>(myParams);
    result.put(Constants.GITHUB_CONTEXT, context);
    return result;
  }

  @NotNull
  String getBuildContext(@NotNull BuildPromotion buildPromotion) {
    String context = getCustomContextFromParameter(buildPromotion);
    return context != null ? context : getDefaultContext(buildPromotion);
  }

  @NotNull
  String getDefaultContext(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType != null) {
      String btName = removeMultiCharUnicodeAndTrim(buildType.getName());
      String prjName = removeMultiCharUnicodeAndTrim(buildType.getProject().getName());
      return String.format("%s (%s)", btName, prjName);
    } else {
      return "<Removed build configuration>";
    }
  }

  private String removeMultiCharUnicodeAndTrim(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c: s.toCharArray()) {
      if (c >= 0xd800L && c <= 0xdfffL || (c & 0xfff0) == 0xfe00 || c == 0x20e3 || c == 0x200d) {
        continue;
      }
      sb.append(c);
    }
    return sb.toString().trim();
  }

  @Nullable
  private String getCustomContextFromParameter(@NotNull BuildPromotion buildPromotion) {
    String value = buildPromotion.getBuildParameters().get(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM);
    if (value == null) {
      return null;
    }
    SBuild associatedBuild = buildPromotion.getAssociatedBuild();
    if (associatedBuild == null) {
      return null;
    }
    return associatedBuild.getValueResolver().resolve(value).getResult();
  }
}
