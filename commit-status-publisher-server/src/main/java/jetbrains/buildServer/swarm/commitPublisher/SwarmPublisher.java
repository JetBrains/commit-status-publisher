package jetbrains.buildServer.swarm.commitPublisher;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.AdditionalTaskInfo;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.HttpBasedCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings.ID;

/**
 * @author kir
 */
class SwarmPublisher extends HttpBasedCommitStatusPublisher {

  private static final String SWARM_TESTRUNS_SUPPORT_ENABLED = "teamcity.swarm.testruns.enabled";
  private static final String SWARM_COMMENTS_NOTIFICATIONS_ENABLED = "teamcity.internal.swarm.commentsNotifications.enabled";

  private final SwarmClient mySwarmClient;
  private final boolean myCreateTestRuns;

  public SwarmPublisher(@NotNull SwarmPublisherSettings swarmPublisherSettings,
                        @NotNull SBuildType buildType,
                        @NotNull String buildFeatureId,
                        @NotNull Map<String, String> params,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull WebLinks links,
                        @NotNull SwarmClient swarmClient) {
    super(swarmPublisherSettings, buildType, buildFeatureId, params, problems, links);
    myCreateTestRuns = StringUtil.isTrue(params.get(SwarmPublisherSettings.PARAM_CREATE_SWARM_TEST));

    mySwarmClient = swarmClient;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String toString() {
    return "perforceSwarm";
  }

  @Override
  public boolean isAvailable(@NotNull BuildPromotion buildPromotion) {
    // Process both ordinary builds and personal builds with shelved changelists
    return true;
  }

  @Nullable
  private String getChangelistId(@NotNull BuildPromotion promotion, @NotNull BuildRevision revision) {
    String shelvedChangelistId = promotion.getParameterValue("vcsRoot." + revision.getRoot().getExternalId() + ".shelvedChangelist");
    if (shelvedChangelistId == null && !promotion.isPersonal()) {
      shelvedChangelistId = revision.getRevisionDisplayName();
    }
    return shelvedChangelistId;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    publishCommentIfNeeded(buildPromotion, revision, "build %s is queued");
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String commentTemplate = "build %s is removed from queue: " + additionalTaskInfo.getCommentOrDefault("<no comment>");
    if (additionalTaskInfo.getCommentAuthor() != null) {
      commentTemplate += " by " + additionalTaskInfo.getCommentAuthor().getDescriptiveName();
    }

    publishCommentIfNeeded(buildPromotion, revision, commentTemplate);
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **has started**");

    if (hasSwarmTestRunSupport()) {
      createTestRunsForReviewsOnSwarm(build, revision);
    }

    return true;
  }

  private void createTestRunsForReviewsOnSwarm(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    postForEachReview(build.getBuildPromotion(), revision, new ReviewMessagePublisher() {
      @Override
      public void publishMessage(@NotNull Long reviewId, @NotNull BuildPromotion buildPromo, @NotNull String debugBuildInfo) throws PublisherException {
        mySwarmClient.createSwarmTestRun(reviewId, build, debugBuildInfo);
      }
    });
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    String result = build.getBuildStatus().isSuccessful() ? "finished successfully" : "failed";
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **has " + result + "** : " + build.getStatusDescriptor().getText());

    updateTestRunsForReviewsOnSwarm(build, revision);

    return true;
  }

  private boolean hasSwarmTestRunSupport() {
    return myCreateTestRuns || TeamCityProperties.getBoolean(SWARM_TESTRUNS_SUPPORT_ENABLED);
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **was interrupted**: " + build.getStatusDescriptor().getText());

    updateTestRunsForReviewsOnSwarm(build, revision);

    return true;
  }

  private void updateTestRunsForReviewsOnSwarm(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {

    if (!hasSwarmTestRunSupport()) {
      return;
    }

    postForEachReview(build.getBuildPromotion(), revision, new ReviewMessagePublisher() {
      @Override
      public void publishMessage(@NotNull Long reviewId, @NotNull BuildPromotion buildPromo, @NotNull String debugBuildInfo) throws PublisherException {
        mySwarmClient.updateSwarmTestRuns(reviewId, build, debugBuildInfo);
      }
    });
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "failure was detected in build %s: " + build.getStatusDescriptor().getText());
    updateTestRunsForReviewsOnSwarm(build, revision);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s was **marked as successful**: " + build.getStatusDescriptor().getText());
    updateTestRunsForReviewsOnSwarm(build, revision);
    return true;
  }

  private void publishCommentIfNeeded(BuildPromotion build, @NotNull BuildRevision revision, @NotNull final String commentTemplate) throws PublisherException {

    boolean commentsNotificationsEnabled = ((BuildPromotionEx)build).getBooleanInternalParameterOrTrue(SWARM_COMMENTS_NOTIFICATIONS_ENABLED);

    postForEachReview(build, revision, new ReviewMessagePublisher() {
      @Override
      public void publishMessage(@NotNull Long reviewId, @NotNull BuildPromotion build, @NotNull String debugBuildInfo) throws PublisherException {
        final String fullComment = "TeamCity: " + String.format(commentTemplate, getBuildMarkdownLink(build)) +
                                   "\nConfiguration: " + myBuildType.getExtendedFullName();

        mySwarmClient.addCommentToReview(reviewId, fullComment, debugBuildInfo, !commentsNotificationsEnabled);
      }
    });
  }

  private void postForEachReview(BuildPromotion build, @NotNull BuildRevision revision, @NotNull final ReviewMessagePublisher messagePublisher) throws PublisherException {

    final String changelistId = getChangelistId(build, revision);
    if (StringUtil.isEmpty(changelistId)) return;

    final SBuildType buildType = build.getBuildType();
    if (buildType == null) return;

    mySwarmClient.setConnectionTimeout(getConnectionTimeout());

    IOGuard.allowNetworkCall(() -> {
      final String debugBuildInfo = "build [id=" + build.getId() + "] in " + buildType.getExtendedFullName();

      for (Long reviewId : mySwarmClient.getOpenReviewIds(changelistId, debugBuildInfo)) {
        messagePublisher.publishMessage(reviewId, build, debugBuildInfo);
      }
    });
  }

  @NotNull
  private String getBuildMarkdownLink(@NotNull BuildPromotion build) {
    SBuild associatedBuild = build.getAssociatedBuild();
    SQueuedBuild queuedBuild = build.getQueuedBuild();
    String url = associatedBuild != null ? myLinks.getViewResultsUrl(associatedBuild) :
                     queuedBuild != null ? myLinks.getQueuedBuildUrl(queuedBuild) :
                     myLinks.getRootUrlByProjectExternalId(build.getProjectExternalId()) + "/build/" + build.getId();
    if (associatedBuild != null) {
      return String.format("#[%s](%s)", associatedBuild.getBuildNumber(), url);
    }
    if (queuedBuild != null) {
      return String.format("#[%s](%s)", queuedBuild.getOrderNumber(), url);
    }
    return String.format("#[%d](%s)", build.getId(), url);
  }

}
