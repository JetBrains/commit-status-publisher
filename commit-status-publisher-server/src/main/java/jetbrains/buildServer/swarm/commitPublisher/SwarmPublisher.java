package jetbrains.buildServer.swarm.commitPublisher;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.userChanges.CanceledInfo;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.swarm.SwarmConstants;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;
import static jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings.ID;

/**
 * @author kir
 */
class SwarmPublisher extends HttpBasedCommitStatusPublisher<String> {

  static final String SWARM_TESTRUNS_DISCOVER = "teamcity.swarm.discover.testruns";
  private static final String SWARM_COMMENTS_NOTIFICATIONS_ENABLED = "teamcity.internal.swarm.commentsNotifications.enabled";

  private final SwarmClient mySwarmClient;
  private final boolean myShouldCreateTestRuns;
  private final Set<Event> myCommentOnEvents;

  public SwarmPublisher(@NotNull SwarmPublisherSettings swarmPublisherSettings,
                        @NotNull SBuildType buildType,
                        @NotNull String buildFeatureId,
                        @NotNull Map<String, String> params,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull WebLinks links,
                        @NotNull SwarmClient swarmClient,
                        @NotNull Set<Event> commentOnEvents) {
    super(swarmPublisherSettings, buildType, buildFeatureId, params, problems, links);
    myShouldCreateTestRuns = StringUtil.isTrue(params.get(SwarmPublisherSettings.PARAM_CREATE_SWARM_TEST));

    mySwarmClient = swarmClient;
    myCommentOnEvents = commentOnEvents;
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
    publishCommentIfNeeded(buildPromotion, revision, "build is queued", Event.QUEUED);
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String comment = getComment(additionalTaskInfo);
    String commentTemplate = "build %s was removed from queue" + (StringUtil.isNotEmpty(comment) ? ": " + comment : "");
    if (additionalTaskInfo.getCommentAuthor() != null) {
      commentTemplate += " by " + additionalTaskInfo.getCommentAuthor().getDescriptiveName();
    }

    publishCommentIfNeeded(buildPromotion, revision, commentTemplate, Event.REMOVED_FROM_QUEUE);


    SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      updateTestRunsForReviewsOnSwarm(build, revision);
    }
    return true;
  }

  @NotNull
  private static String getComment(@NotNull AdditionalTaskInfo additionalTaskInfo) {
    String res = additionalTaskInfo.getComment();
    if (DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE.equals(res)) {
      return "";
    }
    return res;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **has started**", Event.STARTED);

    if (myShouldCreateTestRuns && null == mySwarmClient.getSwarmUpdateUrlFromTriggeringAttr(build)) {
      createTestRunsForReviewsOnSwarm(build, revision);
    }
    else {
      updateTestRunsForReviewsOnSwarm(build, revision);
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
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **has " + result + "** : " + build.getStatusDescriptor().getText(), Event.FINISHED);

    updateTestRunsForReviewsOnSwarm(build, revision);

    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **was interrupted**: " + build.getStatusDescriptor().getText(), Event.INTERRUPTED);

    updateTestRunsForReviewsOnSwarm(build, revision);

    return true;
  }

  private void updateTestRunsForReviewsOnSwarm(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {

    BuildPromotion buildPromotion = build.getBuildPromotion();

    boolean discoverNonTcTestRuns = ((BuildPromotionEx)buildPromotion).getBooleanInternalParameterOrTrue(SWARM_TESTRUNS_DISCOVER);
    boolean tryUpdateSwarmTests = myShouldCreateTestRuns || discoverNonTcTestRuns;

    if (!tryUpdateSwarmTests) {
      return;
    }

    postForEachReview(buildPromotion, revision, new ReviewMessagePublisher() {
      @Override
      public void publishMessage(@NotNull Long reviewId, @NotNull BuildPromotion buildPromo, @NotNull String debugBuildInfo) throws PublisherException {
        mySwarmClient.updateSwarmTestRuns(reviewId, build, debugBuildInfo);
      }
    });
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    final CanceledInfo canceledInfo = build.getCanceledInfo();
    if (canceledInfo != null) {
      LOG.debug(() -> "not publishing build failure, as build " + LogUtil.describe(build) + " is cancelled: " + canceledInfo);
      return false;
    }

    publishCommentIfNeeded(build.getBuildPromotion(), revision, "failure was detected in build %s: " + build.getStatusDescriptor().getText(), Event.FAILURE_DETECTED);
    updateTestRunsForReviewsOnSwarm(build, revision);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s was **marked as successful**: " + build.getStatusDescriptor().getText(), Event.MARKED_AS_SUCCESSFUL);
    updateTestRunsForReviewsOnSwarm(build, revision);
    return true;
  }

  private void publishCommentIfNeeded(BuildPromotion build,
                                      @NotNull BuildRevision revision,
                                      @NotNull final String commentTemplate,
                                      @NotNull Event event) throws PublisherException {

    boolean commentsNotificationsEnabled = ((BuildPromotionEx)build).getBooleanInternalParameterOrTrue(SWARM_COMMENTS_NOTIFICATIONS_ENABLED);
    boolean commentSelectively = ((BuildPromotionEx)build).getBooleanInternalParameterOrTrue(SwarmConstants.FEATURE_ENABLE_COMMENTS_SELECTIVELY);

    if (commentSelectively && !myCommentOnEvents.contains(event)) {
      logStatusNotPublished(build, event, "Comments for this event type have been disabled");
      return;
    }

    PostResult result = postForEachReview(build, revision, new ReviewMessagePublisher() {
      @Override
      public void publishMessage(@NotNull Long reviewId, @NotNull BuildPromotion build, @NotNull String debugBuildInfo) throws PublisherException {
        final String fullComment = "TeamCity: " + buildMessage(build) +
                                   "\nConfiguration: " + myBuildType.getExtendedFullName();

        // Do not send e-mail notification for non-personal builds, they are excessive:
        boolean enableNotification = commentsNotificationsEnabled && build.isPersonal();

        mySwarmClient.addCommentToReview(reviewId, fullComment, debugBuildInfo, !enableNotification);
      }

      private String buildMessage(@NotNull BuildPromotion build) {
        if (commentTemplate.contains("%s")) {
          return String.format(commentTemplate, getBuildMarkdownLink(build));
        }
        else {
          return commentTemplate + String.format(" ([view](%s))", getUrl(build));
        }
      }
    });

    if (!result.isSuccess()) {
      logStatusNotPublished(build, event, result.getMessage());
    }
  }

  private static void logStatusNotPublished(BuildPromotion build, @NotNull Event event, @NotNull String reason) {
    LOG.debug(() -> "Status " + event.getName() + " was not published for build " + LogUtil.describe(build) + ": " + reason + ".");
  }

  @NotNull
  private PostResult postForEachReview(BuildPromotion build, @NotNull BuildRevision revision, @NotNull final ReviewMessagePublisher messagePublisher) throws PublisherException {

    final String changelistId = getChangelistId(build, revision);
    if (StringUtil.isEmpty(changelistId)) return new PostResult(false, "unable to determine changelist ID");

    final SBuildType buildType = build.getBuildType();
    if (buildType == null) return new PostResult(false, "unable to determine build configuration");

    mySwarmClient.setConnectionTimeout(getConnectionTimeout());

    final AtomicBoolean didPost = new AtomicBoolean(false);
    IOGuard.allowNetworkCall(() -> {
      final String debugBuildInfo = "build [id=" + build.getId() + "] in " + buildType.getExtendedFullName();

      for (Long reviewId : mySwarmClient.getOpenReviewIds(changelistId, debugBuildInfo)) {
        messagePublisher.publishMessage(reviewId, build, debugBuildInfo);
        didPost.set(true);
      }
    });

    if (!didPost.get()) {
      return new PostResult(false, "no code reviews found");
    }
    return new PostResult(true, "");
  }

  @NotNull
  private String getBuildMarkdownLink(@NotNull BuildPromotion build) {
    SBuild associatedBuild = build.getAssociatedBuild();
    SQueuedBuild queuedBuild = build.getQueuedBuild();
    String url = getUrl(build);
    if (associatedBuild != null) {
      return String.format("#[%s](%s)", associatedBuild.getBuildNumber(), url);
    }
    if (queuedBuild != null) {
      return String.format("#[%s](%s)", queuedBuild.getOrderNumber(), url);
    }
    return String.format("#[%d](%s)", build.getId(), url);
  }

  @NotNull
  private String getUrl(@NotNull BuildPromotion build) {
    SBuild associatedBuild = build.getAssociatedBuild();
    SQueuedBuild queuedBuild = build.getQueuedBuild();
    return associatedBuild != null ? myLinks.getViewResultsUrl(associatedBuild) :
                 queuedBuild != null ? myLinks.getQueuedBuildUrl(queuedBuild) :
                 myLinks.getRootUrlByProjectExternalId(build.getProjectExternalId()) + "/build/" + build.getId();
  }

  @TestOnly
  protected Set<Event> getCommentOnEvents() {
    return myCommentOnEvents;
  }

  private static class PostResult {
    private final boolean mySuccess;
    @NotNull private final String myMessage;

    private PostResult(boolean success, @NotNull String message) {
      mySuccess = success;
      myMessage = message;
    }

    public boolean isSuccess() {
      return mySuccess;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }
  }
}
