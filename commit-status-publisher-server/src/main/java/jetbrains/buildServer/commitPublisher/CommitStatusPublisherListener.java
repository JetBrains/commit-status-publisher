

package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import com.intellij.openapi.util.Pair;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.MultiNodeTasks.PerformingTask;
import jetbrains.buildServer.serverSide.comments.Comment;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.userChanges.CanceledInfo;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRootEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;
import static jetbrains.buildServer.commitPublisher.ValueWithTTL.OUTDATED_CACHE_VALUE;

public class CommitStatusPublisherListener extends BuildServerAdapter implements ChangesCollectionCondition {

  final static String PUBLISHING_ENABLED_PROPERTY_NAME = "teamcity.commitStatusPublisher.enabled";
  final static String CSP_FOR_BUILD_TYPE_CONFIGURATION_FLAG_TTL_PROPERTY_NAME = "teamcity.commitStatusPublisher.enabledForBuildCache.ttl";
  final static String QUEUE_PAUSER_SYSTEM_PROPERTY = "teamcity.plugin.queuePauser.queue.enabled";
  final static String CHECK_STATUS_BEFORE_PUBLISHING = "teamcity.commitStatusPubliser.checkStatus.enabled";
  final static String LOCKS_STRIPES = "teamcity.commitStatusPublisher.locks.stripes";

  private final static int LOCKS_STRIPES_DEFAULT = 1000;
  private final static int MAX_LAST_EVENTS_TO_REMEMBER = 1000;

  final static String RETRY_ENABLED_PROPERTY_NAME = "teamcity.commitStatusPublisher.retry.enabled";
  final static String RETRY_INITAL_DELAY_PROPERTY_NAME = "teamcity.commitStatusPublisher.retry.initDelayMs";
  final static String RETRY_MAX_DELAY_PROPERTY_NAME = "teamcity.commitStatusPublisher.retry.maxDelayMs";
  final static String RETRY_MAX_TIME_BEFORE_DISABLING = "teamcity.commitStatusPublisher.retry.maxBeforeDisablingMs";
  private final static long DEFAULT_INITIAL_RETRY_DELAY_MS = 10_000;
  private final static long DEFAULT_MAX_RETRY_DELAY_MS = 60 * 60 * 1000; // 30 minutes
  private final static long DEFAULT_MAX_TIME_BEFORE_DISABLING_RETRY = 24 * 60 * 60 * 1000; // 24 hours

  private final PublisherManager myPublisherManager;
  private final BuildHistory myBuildHistory;
  private final BuildsManager myBuildsManager;
  private final BuildPromotionManager myBuildPromotionManager;
  private final CommitStatusPublisherProblems myProblems;
  private final ServerResponsibility myServerResponsibility;
  private final MultiNodeTasks myMultiNodeTasks;
  private final ExecutorServices myExecutorServices;
  private final ProjectManager myProjectManager;
  private final TeamCityNodes myTeamCityNodes;
  private final UserModel myUserModel;
  private final Map<String, Event> myEventTypes = new HashMap<>();
  private final Striped<Lock> myPublishingLocks;
  private final Map<Long, Event> myLastEvents =
    new LinkedHashMap<Long, Event> () {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Long, Event> eldest)
      {
        return size() > MAX_LAST_EVENTS_TO_REMEMBER;
      }
    };
  private final ConcurrentMap<String, ValueWithTTL<Boolean>> myBuildTypeCommitStatusPublisherConfiguredCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Long> myBuildTypeToFirstPublishFailure = new ConcurrentHashMap<>();

  private Consumer<Event> myEventProcessedCallback = null;

  public CommitStatusPublisherListener(@NotNull EventDispatcher<BuildServerListener> events,
                                       @NotNull PublisherManager voterManager,
                                       @NotNull BuildHistory buildHistory,
                                       @NotNull BuildsManager buildsManager,
                                       @NotNull BuildPromotionManager buildPromotionManager,
                                       @NotNull CommitStatusPublisherProblems problems,
                                       @NotNull ServerResponsibility serverResponsibility,
                                       @NotNull final ExecutorServices executorServices,
                                       @NotNull ProjectManager projectManager,
                                       @NotNull TeamCityNodes teamCityNodes,
                                       @NotNull UserModel userModel,
                                       @NotNull MultiNodeTasks multiNodeTasks) {
    myPublisherManager = voterManager;
    myBuildHistory = buildHistory;
    myBuildsManager = buildsManager;
    myBuildPromotionManager = buildPromotionManager;
    myProblems = problems;
    myServerResponsibility = serverResponsibility;
    myTeamCityNodes = teamCityNodes;
    myMultiNodeTasks = multiNodeTasks;
    myExecutorServices = executorServices;
    myProjectManager = projectManager;
    myUserModel = userModel;
    myEventTypes.putAll(Arrays.stream(Event.values()).collect(Collectors.toMap(Event::getName, et -> et)));
    myPublishingLocks = Striped.lazyWeakLock(TeamCityProperties.getInteger(LOCKS_STRIPES, LOCKS_STRIPES_DEFAULT));

    events.addListener(this);

    myMultiNodeTasks.subscribeOnSingletonTask(Event.STARTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          publisher.buildStarted(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.FINISHED.getName(), new BuildPublisherTaskConsumer (
       build -> new PublishTask() {
         @Override
         public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
           publisher.buildFinished(build, revision);
         }
       }
    ));

    myMultiNodeTasks.subscribe(Event.MARKED_AS_SUCCESSFUL.getName(), new BuildPublisherTaskConsumer (
       build -> new PublishTask() {
         @Override
         public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
           publisher.buildMarkedAsSuccessful(build, revision, isBuildInProgress(build));
         }
        }
    ));

    myMultiNodeTasks.subscribe(Event.COMMENTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          Comment comment = build.getBuildComment();
          if (null == comment)
            return;
          publisher.buildCommented(build, revision, comment.getUser(), comment.getComment(), isBuildInProgress(build));
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.INTERRUPTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          publisher.buildInterrupted(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.FAILURE_DETECTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          publisher.buildFailureDetected(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribeOnSingletonTask(Event.QUEUED.getName(), new QueuedBuildPublisherTaskConsumer(
      buildPromotion -> new PublishQueuedTask() {
        @Override
        public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
          publisher.buildQueued(buildPromotion, revision, additionalTaskInfo);
        }
      }
    ));
  }

  private Pair<String, User> getCommentWithAuthor(BuildPromotion buildPromotion) {
    User author = null;
    String comment = null;

    SBuild associatedBuild = buildPromotion.getAssociatedBuild();
    CanceledInfo canceledInfo = associatedBuild != null ? associatedBuild.getCanceledInfo() : null;
    if (canceledInfo != null && canceledInfo.getComment() != null) {
      comment = canceledInfo.getComment();
      if (canceledInfo.getUserId() != null) {
        author = myUserModel.findUserById(canceledInfo.getUserId());
      }
    } else {
      Comment buildComment = buildPromotion.getBuildComment();
      if (buildComment != null) {
        comment = buildComment.getComment();
        author = buildComment.getUser();
      }
    }
    return new Pair<>(comment, author);
  }

  @Override
  public void buildTypeAddedToQueue(@NotNull SQueuedBuild queuedBuild) {  // required only in case of starting build for exact commit
    BuildPromotionEx promotion = (BuildPromotionEx)queuedBuild.getBuildPromotion();
    BuildTypeEx buildType = promotion.getBuildType();

    if (shouldNotPublish(buildType, buildReason(queuedBuild.getTriggeredBy())) || promotion.isChangeCollectingNeeded(true)) return;

    buildAddedToQueue(queuedBuild);
  }

  @Override
  public void changesLoaded(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (shouldNotPublish(buildType, buildReason(buildPromotion))) return;

    SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild != null) {
      buildAddedToQueue(queuedBuild);
    } else {
      SBuild build = buildPromotion.getAssociatedBuild();

      if (build != null && isBuildInProgress(build)) {
        submitTaskForBuild(Event.STARTED, build);
      }
    }
  }

  /**
   * This event is required because new one is not triggered before build start in trivial cases
   * @deprecated it's better to use only {@link changesLoaded(BuildPromotion)} instead
   * @param build build, whose changes are loaded and it's started
   */
  @Deprecated
  @Override
  public void changesLoaded(@NotNull SRunningBuild build) {
    SBuildType buildType = getBuildType(Event.STARTED, build);
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy())))
      return;

    submitTaskForBuild(Event.STARTED, build);
  }

  @Override
  public void buildFinished(@NotNull SRunningBuild build) {
     SBuildType buildType = getBuildType(Event.FINISHED, build);
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy())))
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug(() -> "Event: " + Event.FINISHED + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    submitTaskForBuild(Event.FINISHED, build);
  }

  @Override
  public void buildCommented(@NotNull final SBuild build, @Nullable final User user, @Nullable final String comment) {
    SBuildType buildType = getBuildType(Event.COMMENTED, build);
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy())))
      return;
    submitTaskForBuild(Event.COMMENTED, build);
  }

  @Override
  public void buildInterrupted(@NotNull SRunningBuild build) {
    SBuildType buildType = getBuildType(Event.INTERRUPTED, build);
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy())))
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug(() -> "Event: " + Event.INTERRUPTED.getName() + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    submitTaskForBuild(Event.INTERRUPTED, build);
  }

  private boolean testIfBuildTypeUsingCommitStatusPublisher(SBuildType buildType) {
    ValueWithTTL<Boolean> isCSPEnabled = myBuildTypeCommitStatusPublisherConfiguredCache.getOrDefault(buildType.getInternalId(), OUTDATED_CACHE_VALUE);
    if (isCSPEnabled.isAlive()) {
      return isCSPEnabled.getValue();
    } else {
      myBuildTypeCommitStatusPublisherConfiguredCache.remove(buildType.getInternalId());
    }
    boolean isConfigured = !shouldNotPublish(buildType, BuildReason.TRIGGERED_DIRECTLY);
    myBuildTypeCommitStatusPublisherConfiguredCache.putIfAbsent(buildType.getInternalId(),
                                                                new ValueWithTTL<>(isConfigured, System.currentTimeMillis() + TeamCityProperties.getIntervalMilliseconds(CSP_FOR_BUILD_TYPE_CONFIGURATION_FLAG_TTL_PROPERTY_NAME, 5 * 60 * 1000)));
    return isConfigured;
  }

  @Override
  public void buildChangedStatus(@NotNull final SRunningBuild build, Status oldStatus, Status newStatus) {
    if (oldStatus.isFailed() || !newStatus.isFailed()) // we are supposed to report failures only
      return;

    SBuildType buildType = getBuildType(Event.FAILURE_DETECTED, build);
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy())))
      return;

    submitTaskForBuild(Event.FAILURE_DETECTED, build);
  }


  @Override
  public void buildProblemsChanged(@NotNull final SBuild build, @NotNull final List<BuildProblemData> before, @NotNull final List<BuildProblemData> after) {
    SBuildType buildType = getBuildType(Event.MARKED_AS_SUCCESSFUL, build);
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy())))
      return;

    if (!before.isEmpty() && after.isEmpty()) {
      submitTaskForBuild(Event.MARKED_AS_SUCCESSFUL, build);
    }
  }

  private void buildAddedToQueue(@NotNull SQueuedBuild build) {
    if (isQueueDisabled()) return;
    if (!shouldPublishQueuedEvent(build)) return;

    BuildPromotion buildPromotion = build.getBuildPromotion();

    submitTaskForQueuedBuild(Event.QUEUED, buildPromotion, null);
  }

  public boolean isQueueDisabled() {
    String isQueueEnabled = System.getProperty(QUEUE_PAUSER_SYSTEM_PROPERTY);
    return Boolean.FALSE.toString().equalsIgnoreCase(isQueueEnabled);
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    if (isQueueDisabled()) return;

    SBuildType buildType = getBuildType(Event.REMOVED_FROM_QUEUE, build);
    if (build instanceof QueuedBuildEx && ((QueuedBuildEx) build).isStarted()) return;
    if (shouldNotPublish(buildType, buildReason(build.getTriggeredBy()))) return;

    if (isPublishingDisabled(build.getBuildType())) {
      LOG.info(String.format("Event: %s, build: %s: commit status publishing is disabled", Event.REMOVED_FROM_QUEUE, LogUtil.describe(build)));
      return;
    }

    BuildPromotion promotion = build.getBuildPromotion();

    // build removal is processed only for manually removed from queue or canceled/failed to start(due to failed dependency) builds
    if (!promotion.isCanceled()) {
      SBuild associatedBuild = promotion.getAssociatedBuild();
      //check if build failed to start, if it's not then we shouldn't post status
      if (associatedBuild == null || !associatedBuild.getBuildStatus().isFailed()) {
        return;
      }
    }

    if (!canNodeProcessRemovedFromQueue(promotion)) return;
    if (((BuildPromotionEx)promotion).isChangeCollectingNeeded(false)) return;

    runAsync(() -> {
      proccessRemovedFromQueueBuild(build, user, comment);
    }, null);
  }

  private boolean canNodeProcessRemovedFromQueue(BuildPromotion buildPromotion) {
    String creatorNodeId = buildPromotion.getCreatorNodeId();
    String currentNodeId = CurrentNodeInfo.getNodeId();
    if (creatorNodeId.equals(currentNodeId)) return true;  // allowed to process on node, where promotion was cereated

    List<TeamCityNode> onlineNodes = myTeamCityNodes.getOnlineNodes();
    boolean isCreatorNodeOnline = onlineNodes.stream()
                                             .map(TeamCityNode::getId)
                                             .anyMatch(nodeId -> nodeId.equals(creatorNodeId));
    if (isCreatorNodeOnline) return false; // should process on online node, where promotion was created (not this one)

    return CurrentNodeInfo.isMainNode(); // node that created promotion is offline, should be processes on main node
  }

  @Used("tests")
  void setEventProcessedCallback(@Nullable Consumer<Event> callback) {
    myEventProcessedCallback = callback;
  }

  private void eventProcessed(Event event) {
    if (myEventProcessedCallback != null)
      myEventProcessedCallback.accept(event);
  }

  private boolean shouldNotPublish(@Nullable SBuildType buildType, @NotNull BuildReason buildReason) {
    return isBuildFeatureAbsent(buildType) && !myPublisherManager.isFeatureLessPublishingPossible(buildType, buildReason);
  }

  private boolean shouldPublishQueuedEvent(@NotNull SQueuedBuild queuedBuild) {
    final BuildPromotion buildPromotion = queuedBuild.getBuildPromotion();
    if (buildPromotion.isPartOfBuildChain() && buildPromotion.getContainingChanges().isEmpty() && buildPromotion.getNumberOfDependedOnMe() != 0) {
      LOG.debug(() -> String.format("The build #%s is part of a build chain and has no new changes in it. Queued build status will not be published as Commit Status Publisher suspects that the build can be optimized away.", queuedBuild.getItemId()));
      return false;
    }

    return true;
  }

  private boolean isBuildFeatureAbsent(@Nullable SBuildType buildType) {
    return buildType == null || buildType.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE).stream()
                                         .noneMatch(f -> buildType.isEnabled(f.getId()));
  }

  @NotNull
  private BuildReason buildReason(@NotNull TriggeredBy triggeredBy) {
    if (triggeredBy.isTriggeredBySnapshotDependency()) {
      return BuildReason.TRIGGERED_AS_DEPENDENCY;
    }
    return BuildReason.TRIGGERED_DIRECTLY;
  }

  @NotNull
  private BuildReason buildReason(@NotNull BuildPromotion buildPromotion) {
    final SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      return buildReason(build.getTriggeredBy());
    }

    final SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild != null) {
      return buildReason(queuedBuild.getTriggeredBy());
    }

    LOG.debug(() -> "unable to determine triggeredBy of build promotion " + LogUtil.describe(buildPromotion));
    return BuildReason.UNKNOWN;
  }


  private boolean isPublishingDisabled(SBuildType buildType) {
    String publishingEnabledParam = buildType.getParameterValue(PUBLISHING_ENABLED_PROPERTY_NAME);
    return "false".equals(publishingEnabledParam)
           || !(TeamCityProperties.getBooleanOrTrue(PUBLISHING_ENABLED_PROPERTY_NAME)
                || "true".equals(publishingEnabledParam));
  }

  private boolean isRetryEnabled() {
    return TeamCityProperties.getBooleanOrTrue(RETRY_ENABLED_PROPERTY_NAME);
  }

  private long initialRetryDelay() {
    return TeamCityProperties.getLong(RETRY_INITAL_DELAY_PROPERTY_NAME, DEFAULT_INITIAL_RETRY_DELAY_MS);
  }

  private long maxRetryDelay() {
    return TeamCityProperties.getLong(RETRY_MAX_DELAY_PROPERTY_NAME, DEFAULT_MAX_RETRY_DELAY_MS);
  }

  private long maxBeforeDisablingRetry() {
    return TeamCityProperties.getLong(RETRY_MAX_TIME_BEFORE_DISABLING, DEFAULT_MAX_TIME_BEFORE_DISABLING_RETRY);
  }

  private void logStatusNotPublished(@NotNull Event event, @NotNull String buildDescription, @NotNull CommitStatusPublisher publisher, @NotNull String message) {
    LOG.info(String.format("Event: %s, build %s, publisher %s: %s", event.getName(), buildDescription, publisher, message));
  }

  private void submitTaskForBuild(@NotNull Event event, @NotNull SBuild build) {
    submitTaskForBuild(event, build, null);
  }

  private String getTaskIdentity(@NotNull Event event, long id, @Nullable Long delay) {
    String identity= event.getName() + ":" + id;
    if (delay != null) {
      identity += ":delay" + delay;
    }
    return identity;
  }

  private void submitTaskForBuild(@NotNull Event event, @NotNull SBuild build, @Nullable Long delay) {
    if  (!myServerResponsibility.isResponsibleForBuild(build)) {
      LOG.debug(() -> "Current node is not responsible for build " + LogUtil.describe(build) + ", skip processing event " + event);
      return;
    }

    long buildId = build.getBuildId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(event.getName(), getTaskIdentity(event, buildId, delay), buildId, delay, (String)null));
  }

  private void submitTaskForQueuedBuild(@NotNull Event event, @NotNull BuildPromotion buildPromotion, @Nullable Long delay) {
    long promotionId = buildPromotion.getId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(Event.QUEUED.getName(), getTaskIdentity(event, promotionId, delay), promotionId, delay, DefaultStatusMessages.BUILD_QUEUED));
  }

  private boolean isCurrentRevisionSuitable(Event event, BuildPromotion buildPromotion, BuildRevision revision, CommitStatusPublisher publisher) throws PublisherException {
    if (TeamCityProperties.getBooleanOrTrue(CHECK_STATUS_BEFORE_PUBLISHING)) {
      RevisionStatus revisionStatus = publisher.getRevisionStatus(buildPromotion, revision);
      return revisionStatus == null || revisionStatus.isEventAllowed(event, buildPromotion.getId());
    }
    return true;
  }

  private void proccessRemovedFromQueueBuild(SQueuedBuild queuedBuild, User user, String comment) {
    BuildPromotion buildPromotion = queuedBuild.getBuildPromotion();
    AdditionalTaskInfo additionalTaskInfo = buildAdditionalRemovedFromQueueInfo(buildPromotion, comment, user);

    PublishingProcessor publishingProcessor = new PublishingProcessor() {
      @Override
      public RetryInfo publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
        SBuildType buildType = buildPromotion.getBuildType();
        if (buildType == null) {
          return new RetryInfo();
        }
        if (!publisher.isAvailable(buildPromotion)) return new RetryInfo();

        Lock lock = myPublishingLocks.get(getLockKey(buildType, revision));
        lock.lock();
        try {
          publisher.buildRemovedFromQueue(buildPromotion, revision, additionalTaskInfo);
        } catch (PublisherException e) {
          LOG.warn("Cannot publish removed build status to VCS for " + publisher.getBuildType() + ", commit: " + revision.getRevision(), e);
        } finally {
          lock.unlock();
        }
        return new RetryInfo();
      }

      @Override
      public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
        return getQueuedBuildRevisionForVote(buildType, publisher, buildPromotion);
      }
    };
    proccessPublishing(Event.REMOVED_FROM_QUEUE, buildPromotion, publishingProcessor);
  }

  private String getLockKey(SBuildType buildType, BuildRevision revision) {
    return buildType.getBuildTypeId() + ":" + revision.getRevision();
  }

  private void proccessPublishing(Event event, BuildPromotion buildPromotion, PublishingProcessor publishingProcessor) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType == null) {
      LOG.warn("Build status has not been published: build type not found, id: " + buildPromotion.getBuildTypeExternalId());
      return;
    }
    Map<String, CommitStatusPublisher> publishers = getPublishers(buildType);
    LOG.debug(() -> "Event: " + event.getName() + ", build promotion " + LogUtil.describe(buildPromotion) + ", publishers: " + publishers.values());
    for (CommitStatusPublisher publisher : publishers.values()) {
      if (!publisher.isEventSupported(event))
        continue;
      if (isPublishingDisabled(buildType)) {
        logStatusNotPublished(event, LogUtil.describe(buildPromotion), publisher, "commit status publishing is disabled");
        continue;
      }
      Collection<BuildRevision> revisions = publishingProcessor.getRevisions(buildType, publisher);
      if (revisions.isEmpty()) {
        logStatusNotPublished(event, LogUtil.describe(buildPromotion), publisher, "no compatible revisions found");
        continue;
      }
      myProblems.clearProblem(publisher);
      List<RetryInfo> retryResults = revisions.stream().map(revision -> publishingProcessor.publish(event, revision, publisher)).collect(Collectors.toList());
      RetryInfo retryInfo = new RetryInfo();
      for (RetryInfo info : retryResults) {
        if (info.shouldRetry) {
          retryInfo = info;
          break;
        }
      }

      if (retryInfo.shouldRetry) {
        if (event == Event.QUEUED) {
          submitTaskForQueuedBuild(event, buildPromotion, retryInfo.newDelay);
        } else {
          final SBuild build = buildPromotion.getAssociatedBuild();
          if (build != null) {
            submitTaskForBuild(event, build, retryInfo.newDelay);
          }
        }
      }
    }
  }

  private AdditionalTaskInfo buildAdditionalRemovedFromQueueInfo(BuildPromotion buildPromotion, String comment, User user) {
    User actualCommentAuthor;
    if (comment != null) {
      actualCommentAuthor = user;
    } else {
      Pair<String, User> commentWithAuthor = getCommentWithAuthor(buildPromotion);
      actualCommentAuthor = commentWithAuthor.getSecond();
    }
    BuildPromotion replacingPromotion = getReplacingPromotion(buildPromotion);
    String overridenComment = getDefaultComment(buildPromotion, replacingPromotion, user);
    return new AdditionalTaskInfo(buildPromotion, overridenComment, actualCommentAuthor, replacingPromotion);
  }

  @Nullable
  private BuildPromotion getReplacingPromotion(@NotNull BuildPromotion buildPromotion) {
    BuildPromotion replacingPromotion = myBuildPromotionManager.findPromotionOrReplacement(buildPromotion.getId());
    return (replacingPromotion == null || replacingPromotion.getId() == buildPromotion.getId()) ? null : replacingPromotion;
  }

  @NotNull
  private static String getDefaultComment(@NotNull BuildPromotion buildPromotion, @Nullable BuildPromotion replacingPromotion, @Nullable User user) {
    if (replacingPromotion == null && buildPromotion.getAssociatedBuildId() != null && user == null) {
      return DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED;
    }
    return DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE;
  }

  @Nullable
  private SBuildType getBuildType(@NotNull Event event, @NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      LOG.debug(() -> "Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build));
    return buildType;
  }

  @Nullable
  private SBuildType getBuildType(@NotNull Event event, @NotNull SQueuedBuild build) {
    try {
      return build.getBuildType();
    } catch (BuildTypeNotFoundException e) {
      LOG.debug(() -> "Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build));
      return null;
    }
  }

  private interface PublishTask {
    void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException;
  }

  private interface PublishQueuedTask {
    void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException;
  }

  private boolean isBuildInProgress(SBuild build) {
    return myBuildsManager.findRunningBuildById(build.getBuildId()) != null;
  }

  private boolean shouldFailBuild(@NotNull SBuildType buildType) {
    return Boolean.parseBoolean(buildType.getParameters().get("teamcity.commitStatusPublisher.failBuildOnPublishError"));
  }

  @NotNull
  private Collection<BuildRevision> getQueuedBuildRevisionForVote(@NotNull BuildType buildType,
                                                            @NotNull CommitStatusPublisher publisher,
                                                            @NotNull BuildPromotion buildPromotion) {
    if (buildPromotion.isFailedToCollectChanges()) return publisher.getFallbackRevisions(buildPromotion.getAssociatedBuild());

    if (!((BuildPromotionEx)buildPromotion).isChangeCollectingNeeded(false)) {
      return getBuildRevisionForVote(publisher, buildPromotion.getRevisions(), buildPromotion.getBranch());
    }
    LOG.debug(() -> "No revision is found for build " + buildPromotion.getBuildTypeExternalId() + ". Queue-related status won't be published");
    return Collections.emptyList();
  }

  @NotNull
  private List<BuildRevision> getBuildRevisionForVote(@NotNull CommitStatusPublisher publisher,
                                                      @NotNull Collection<BuildRevision> revisionsToCheck,
                                                      @Nullable Branch buildBranch) {
    if (revisionsToCheck.isEmpty()) return Collections.emptyList();

    String vcsRootId = publisher.getVcsRootId();
    if (vcsRootId == null) {
      List<BuildRevision> revisions = new ArrayList<BuildRevision>();
      for (BuildRevision revision : revisionsToCheck) {
        if (publisher.isPublishingForRevision(revision)) {
          revisions.add(revision);
        }
      }
      // TW-79075
      if (buildBranch != null && !buildBranch.isDefaultBranch()) {
        Set<String> selectedBranchNames = new HashSet<>();
        selectedBranchNames.add(buildBranch.getName());
        selectedBranchNames.add("refs/heads/" + buildBranch.getName());  // git prepends this to the branch name
        List<BuildRevision> revisionsForBranch = new ArrayList<>();
        for(BuildRevision revision: revisions) {
          String vcsBranch = revision.getRepositoryVersion().getVcsBranch();
          if (vcsBranch != null && selectedBranchNames.contains(vcsBranch)) {
            revisionsForBranch.add(revision);
          }
        }
        if (!revisionsForBranch.isEmpty()) {
          LOG.info("Selective publishing for buildBranch=" + buildBranch + ", selected "
            + revisionsForBranch.stream().map(r -> r.getRoot().getName() + '@' + r.getRepositoryVersion().getVcsBranch()).collect(Collectors.joining(", ", "[", "]"))
            + " out of " + revisions.stream().map(r -> r.getRoot().getName() + '@' + r.getRepositoryVersion().getVcsBranch()).collect(Collectors.joining(", ", "[", "]"))
          );
          revisions = revisionsForBranch;
        } else {
          LOG.warn("Attempted selective publishing, but did not find revisions for buildBranch=" + buildBranch + " among "
            + revisions.stream().map(r -> r.getRoot().getName() + '@' + r.getRepositoryVersion().getVcsBranch()).collect(Collectors.joining(", ", "[", "]"))
            + ", will publish all");
        }
      }
      return revisions;
    }

    for (BuildRevision revision : revisionsToCheck) {
      SVcsRootEx root = (SVcsRootEx)revision.getRoot().getParent();
      if (vcsRootId.equals(root.getExternalId()) || root.isAliasExternalId(vcsRootId) || vcsRootId.equals(String.valueOf(root.getId())))
        return Arrays.asList(revision);
    }

    return Collections.emptyList();
  }

  private boolean shouldCollectChangesNow(@NotNull SBuildType buildType) {
    if (!((BuildTypeEx)buildType).getBooleanInternalParameterOrTrue(PUBLISHING_ENABLED_PROPERTY_NAME)) return false;

    return testIfBuildTypeUsingCommitStatusPublisher(buildType);
  }

  @NotNull
  @Override
  public Result shouldCollectChangesNow(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType == null) return Result.UNKNOWN;

    return shouldCollectChangesNow(buildType) ? Result.YES : Result.UNKNOWN;
  }

  @NotNull
  private Map<String, CommitStatusPublisher> getPublishers(@NotNull SBuildType buildType) {
    final Map<String, CommitStatusPublisher> publishers = myPublisherManager.createConfiguredPublishers(buildType);

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(buildType, Collections.unmodifiableMap(publishers));
    publishers.putAll(supplementaryPublishers);

    return publishers;
  }

  private interface PublishingProcessor {
    RetryInfo publish(Event event, BuildRevision revision, CommitStatusPublisher publisher);
    Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher);
  }

  private void runAsync(@NotNull Runnable action, @Nullable Runnable postAction) {
    try {
      CompletableFuture<Void> future = CompletableFuture.runAsync(action, myExecutorServices.getLowPriorityExecutorService());
      if (postAction != null) {
        future.handle((r, t) -> {
          postAction.run();
          return r;
        });
      }
    } catch (RejectedExecutionException ex) {
      LOG.warnAndDebugDetails("CommitStatusPublisherListener has failed to run an action asynchronously. Executing in the same thread instead", ex);
      action.run();
      if (postAction != null)
        postAction.run();
    }
  }

  private class BuildPublisherTaskConsumer extends PublisherTaskConsumer<PublishTask> {

    private final Function<SBuild, PublishTask> myTaskSupplier;

    protected BuildPublisherTaskConsumer(Function<SBuild, PublishTask> taskSupplier) {
      myTaskSupplier = taskSupplier;
    }

    @Override
    public boolean beforeAccept(@NotNull final PerformingTask task) {
      Long buildId = task.getLongArg1();
      if (buildId == null) return false;

      Long delay = task.getLongArg2();
      if (delay != null && task.getCreateTime().getTime() + delay > Instant.now().toEpochMilli()) {
        return false;
      }

      SBuild build = myBuildsManager.findBuildInstanceById(buildId);
      if (build == null) {
        return myServerResponsibility.canManageBuilds();
      }

      return myServerResponsibility.isResponsibleForBuild(build);
    }

    @Override
    public void accept(final PerformingTask task) {
      Event eventType = getEventType(task);
      SBuild build = getBuild(task);

      // We are accepting the task. It will be either completed or will fail
      // One way or another it will be marked as finished (see TW-69618)
      task.finished();
      if (eventType == null || build == null) {
        eventProcessed(eventType);
        return;
      }

      synchronized (myLastEvents) {
        BuildPromotion buildPromotion = build.getBuildPromotion();
        if (myLastEvents.get(buildPromotion.getId()) != null && eventType.isFirstTask()) {
          eventProcessed(eventType);
          return;
        }
        if (eventType.isConsequentTask())
          myLastEvents.put(buildPromotion.getId(), eventType);
      }

      Long lastDelay = task.getLongArg2();
      if (lastDelay != null && eventType == Event.STARTED && build.isFinished()) {
        return;
      }
      runAsync(() -> runForEveryPublisher(eventType, build, lastDelay), () -> { eventProcessed(eventType); });
    }

    @Nullable
    private SBuild getBuild(final PerformingTask task) {
      Long buildId = task.getLongArg1();
      if (buildId == null) return null;

      return myBuildsManager.findBuildInstanceById(buildId);
    }

    @Override
    void doRunTask(PublishTask task, CommitStatusPublisher publisher, BuildRevision revision, AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
      task.run(publisher, revision);
    }

    private void runForEveryPublisher(@NotNull Event event, @NotNull SBuild build, @Nullable Long lastDelay) {
      PublishTask task = myTaskSupplier.apply(build);
      SBuildType buildType = build.getBuildType();
      if (buildType == null) return;

      final BuildPromotion buildPromotion = build.getBuildPromotion();
      PublishingProcessor publishingProcessor = new PublishingProcessor() {
        @Override
        public RetryInfo publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
          RetryInfo retryInfo = new RetryInfo();
          if (!publisher.isAvailable(buildPromotion)) return retryInfo;

          Lock lock = myPublishingLocks.get(revision.getRevision());
          lock.lock();

          try {
            boolean isEventSuitableForRevision = true;
            if (event.canOverrideStatus()) {
              try {
                isEventSuitableForRevision = isCurrentRevisionSuitable(event, buildPromotion, revision, publisher);
              } catch (PublisherException e) {
                retryInfo = getRetryInfo(e, buildPromotion, event, lastDelay);
                LOG.warnAndDebugDetails("Cannot determine if event \"" + event + "\" can be published for current revision state in VCS. " + retryInfo.message, e);
                return retryInfo;
              }
            }
            if (isEventSuitableForRevision) {
              retryInfo = runTask(event, buildPromotion, LogUtil.describe(build), task, publisher, revision, null, lastDelay);
            } else {
              LOG.debug(() -> "Event \"" + event + "\" is not suitable to be published to root \"" + publisher.getVcsRootId() + "\" for revision " + revision.getRevision());
            }
          } finally {
            lock.unlock();
          }
          return retryInfo;
        }

        @Override
        public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
          if (buildPromotion.isFailedToCollectChanges()) return publisher.getFallbackRevisions(build);
          return getBuildRevisionForVote(publisher, build.getRevisions(), build.getBranch());
        }
      };

      proccessPublishing(event, buildPromotion, publishingProcessor);
    }

  }

  private class QueuedBuildPublisherTaskConsumer extends PublisherTaskConsumer<PublishQueuedTask> {

    private final Function<BuildPromotion, PublishQueuedTask> myTaskSupplier;

    QueuedBuildPublisherTaskConsumer(Function<BuildPromotion, PublishQueuedTask> taskSupplier) {
      myTaskSupplier = taskSupplier;
    }

    @Override
    public boolean beforeAccept(@NotNull PerformingTask task) {
      Long delay = task.getLongArg2();
      return delay == null || task.getCreateTime().getTime() + delay < Instant.now().toEpochMilli();
    }

    @Override
    public void accept(final PerformingTask task) {
      Event eventType = getEventType(task);
      BuildPromotion promotion = getBuildPromotion(task);

      task.finished();
      if (eventType == null || promotion == null) {
        eventProcessed(eventType);
        return;
      }

      Event event = myLastEvents.get(promotion.getId());
      if (event != null && event == Event.STARTED) {
        eventProcessed(event);
        return;
      }

      User commentAuthor = getUser(task);
      String comment = getComment(task);
      AdditionalTaskInfo additionalTaskInfo = new AdditionalTaskInfo(promotion, comment, commentAuthor);

      Long lastDelay = task.getLongArg2();
      if (lastDelay != null && event == Event.QUEUED && promotion.getQueuedBuild() == null) {
        return;
      }
      runAsync(() -> runForEveryPublisher(eventType, promotion, additionalTaskInfo, lastDelay), () -> { eventProcessed(eventType); });
    }

    @Nullable
    private BuildPromotion getBuildPromotion(final  PerformingTask task) {
      Long promotionId = task.getLongArg1();
      if (promotionId == null)
        return null;

      return myBuildPromotionManager.findPromotionById(promotionId);
    }

    @Nullable
    protected User getUser(PerformingTask task) {
      Long userId = task.getLongArg2();
      return userId == null ? null : myUserModel.findUserById(userId);
    }

    @Nullable
    protected String getComment(PerformingTask task) {
      return task.getStringArg();
    }

    @Override
    void doRunTask(PublishQueuedTask task, CommitStatusPublisher publisher, BuildRevision revision, AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
      task.run(publisher, revision, additionalTaskInfo);
    }

    private void runForEveryPublisher(@NotNull Event event, @NotNull BuildPromotion buildPromotion, AdditionalTaskInfo additionalTaskInfo, @Nullable Long lastDelay) {
      PublishQueuedTask publishTask = myTaskSupplier.apply(buildPromotion);

      PublishingProcessor publishingProcessor = new PublishingProcessor() {
        @Override
        public RetryInfo publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
          RetryInfo retryInfo = new RetryInfo();
          SBuildType buildType = buildPromotion.getBuildType();
          if (buildType == null) {
            return retryInfo;
          }
          if (!publisher.isAvailable(buildPromotion)) return retryInfo;

          Lock lock = myPublishingLocks.get(revision.getRevision());
          lock.lock();
          try {
            retryInfo = doPublish(revision, publisher);
          } finally {
            lock.unlock();
          }
          return retryInfo;
        }

        private RetryInfo doPublish(BuildRevision revision, CommitStatusPublisher publisher) {
          boolean isEventSuitableForRevision;
          RetryInfo retryInfo = new RetryInfo();
          try {
            isEventSuitableForRevision = isCurrentRevisionSuitable(event, buildPromotion, revision, publisher);
          } catch (PublisherException e) {
            retryInfo = getRetryInfo(e, buildPromotion, event, lastDelay);
            LOG.warnAndDebugDetails("Cannot determine if event \"" + event + "\" can be published for current revision state in VCS. " + retryInfo.message, e);
            return retryInfo;
          }
          if (isEventSuitableForRevision) {
            retryInfo = runTask(event, buildPromotion, LogUtil.describe(buildPromotion), publishTask, publisher, revision, additionalTaskInfo, lastDelay);
          } else {
            LOG.debug(() -> "Event \"" + event + "\" is not suitable to be published to root \"" + publisher.getVcsRootId() + "\" for revision " + revision.getRevision());
          }
          return retryInfo;
        }

        @Override
        public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
          return getQueuedBuildRevisionForVote(buildType, publisher, buildPromotion);
        }
      };
      proccessPublishing(event, buildPromotion, publishingProcessor);
    }

  }

  private static class RetryInfo {
    final boolean shouldRetry;
    @NotNull
    final String message;
    final long newDelay;
    RetryInfo(boolean shouldRetry, @NotNull String message, long newDelay) {
      this.message = message;
      this.newDelay = newDelay;
      this.shouldRetry = shouldRetry;
    }

    RetryInfo() {
      message = "";
      newDelay = 0;
      shouldRetry = false;
    }
  }

  @NotNull
  private RetryInfo getRetryInfo(@NotNull Throwable t, @NotNull BuildPromotion buildPromotion, @NotNull Event event, @Nullable Long lastDelay) {
    if (isRetryEnabled() && event.isRetryable() && t instanceof PublisherException && ((PublisherException)t).shouldRetry()) {
      Long firstRetry = myBuildTypeToFirstPublishFailure.get(buildPromotion.getBuildTypeId());
      long timeNow = Instant.now().toEpochMilli();
      if (firstRetry != null) {
        if (timeNow - firstRetry > maxBeforeDisablingRetry()) {
          return new RetryInfo(false, "Retry will not be attempted, because problem occurs for too long", 0);
        }
      } else {
        myBuildTypeToFirstPublishFailure.put(buildPromotion.getBuildTypeId(), timeNow);
      }

      final long newDelay = lastDelay == null ? initialRetryDelay() : lastDelay * 2;
      if (newDelay > maxRetryDelay()) {
        return new RetryInfo(false, "Retry will not be attempted, becuase max retry delay is reached", 0);
      }
      return new RetryInfo(true, String.format("Will retry in %d seconds", newDelay / 1000), newDelay);
    }
    return new RetryInfo();
  }

  private abstract class PublisherTaskConsumer<T> extends MultiNodeTasks.TaskConsumer {

    abstract void doRunTask(T task, CommitStatusPublisher publisher, BuildRevision revision, AdditionalTaskInfo additionalTaskInfo) throws PublisherException;

    @Nullable
    protected Event getEventType(PerformingTask task) {
      String taskType = task.getType();
      return myEventTypes.get(taskType);
    }

    protected RetryInfo runTask(@NotNull Event event,
                           @NotNull BuildPromotion promotion,
                           @NotNull String buildDescription,
                           @NotNull T publishTask,
                           @NotNull CommitStatusPublisher publisher,
                           @NotNull BuildRevision revision,
                           @Nullable AdditionalTaskInfo additionalTaskInfo,
                           @Nullable Long lastDelay) {
      RetryInfo retryInfo = new RetryInfo();
      try {
        LOG.info(String.format("Publishing status to %s: build id %d, revision %s, event %s", publisher.getSettings().getName(), promotion.getId(), revision.getRevision(), event.getName()));
        doRunTask(publishTask, publisher, revision, additionalTaskInfo);
        myBuildTypeToFirstPublishFailure.remove(promotion.getBuildTypeId());
      } catch (Throwable t) {
        retryInfo = getRetryInfo(t, promotion, event, lastDelay);
        String problemMessage = String.format("Commit Status Publisher has failed to publish %s status", event.getName());
        if (!retryInfo.message.isEmpty()) {
          problemMessage = problemMessage + ". " + retryInfo.message;
        }
        myProblems.reportProblem(problemMessage, publisher, buildDescription, null, t, LOG);
        if (shouldFailBuild(publisher.getBuildType())) {
          String problemId = "commitStatusPublisher." + publisher.getId() + "." + revision.getRoot().getId();
          String problemDescription = t instanceof PublisherException ? t.getMessage() : t.toString();
          BuildProblemData buildProblem = BuildProblemData.createBuildProblem(problemId, "commitStatusPublisherProblem", problemDescription);
          ((BuildPromotionEx)promotion).addBuildProblem(buildProblem);
        }
      }
      return retryInfo;
    }
  }
}