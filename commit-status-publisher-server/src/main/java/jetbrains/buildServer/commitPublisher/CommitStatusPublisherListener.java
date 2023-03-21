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

package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    if (promotion.isChangeCollectingNeeded(true)) {
      return;
    }
    buildAddedToQueue(queuedBuild);
  }

  @Override
  public void changesLoaded(@NotNull BuildPromotion buildPromotion) {
    SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild != null) {
      if (buildPromotion.isPartOfBuildChain() && buildPromotion.getContainingChanges().isEmpty() && buildPromotion.getNumberOfDependedOnMe() != 0) {
        LOG.debug(String.format("Queued status for build #%s will not be published, because it will be optimized", queuedBuild.getItemId()));
        return;
      }
      buildAddedToQueue(queuedBuild);
    } else {
      SBuild build = buildPromotion.getAssociatedBuild();
      SBuildType buildType = buildPromotion.getBuildType();
      if (build == null || isBuildFeatureAbsent(buildType)) return;

      if (isBuildInProgress(build)) {
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
    if (isBuildFeatureAbsent(buildType))
      return;

    submitTaskForBuild(Event.STARTED, build);
  }

  @Override
  public void buildFinished(@NotNull SRunningBuild build) {
     SBuildType buildType = getBuildType(Event.FINISHED, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug("Event: " + Event.FINISHED + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    submitTaskForBuild(Event.FINISHED, build);
  }

  @Override
  public void buildCommented(@NotNull final SBuild build, @Nullable final User user, @Nullable final String comment) {
    SBuildType buildType = getBuildType(Event.COMMENTED, build);
    if (isBuildFeatureAbsent(buildType))
      return;
    submitTaskForBuild(Event.COMMENTED, build);
  }

  @Override
  public void buildInterrupted(@NotNull SRunningBuild build) {
    SBuildType buildType = getBuildType(Event.INTERRUPTED, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug("Event: " + Event.INTERRUPTED.getName() + ", cannot find finished build for build " + LogUtil.describe(build));
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
    boolean isConfigured = !isBuildFeatureAbsent(buildType);
    myBuildTypeCommitStatusPublisherConfiguredCache.putIfAbsent(buildType.getInternalId(),
                                                                new ValueWithTTL<>(isConfigured, System.currentTimeMillis() + TeamCityProperties.getIntervalMilliseconds(CSP_FOR_BUILD_TYPE_CONFIGURATION_FLAG_TTL_PROPERTY_NAME, 5 * 60 * 1000)));
    return isConfigured;
  }

  @Override
  public void buildChangedStatus(@NotNull final SRunningBuild build, Status oldStatus, Status newStatus) {
    if (oldStatus.isFailed() || !newStatus.isFailed()) // we are supposed to report failures only
      return;

    SBuildType buildType = getBuildType(Event.FAILURE_DETECTED, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    submitTaskForBuild(Event.FAILURE_DETECTED, build);
  }


  @Override
  public void buildProblemsChanged(@NotNull final SBuild build, @NotNull final List<BuildProblemData> before, @NotNull final List<BuildProblemData> after) {
    SBuildType buildType = getBuildType(Event.MARKED_AS_SUCCESSFUL, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    if (!before.isEmpty() && after.isEmpty()) {
      submitTaskForBuild(Event.MARKED_AS_SUCCESSFUL, build);
    }
  }

  private void buildAddedToQueue(@NotNull SQueuedBuild build) {
    if (isQueueDisabled()) return;

    SBuildType buildType = getBuildType(Event.QUEUED, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    BuildPromotion buildPromotion = build.getBuildPromotion();
    if (isCreatedOnOtherNode(buildPromotion)) return;

    long promotionId = buildPromotion.getId();
    String identity = Event.QUEUED.getName() + ":" + promotionId;
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(Event.QUEUED.getName(), identity, promotionId, null, DefaultStatusMessages.BUILD_QUEUED));
  }

  public boolean isQueueDisabled() {
    String isQueueEnabled = System.getProperty(QUEUE_PAUSER_SYSTEM_PROPERTY);
    return Boolean.FALSE.toString().equalsIgnoreCase(isQueueEnabled);
  }

  private boolean isCreatedOnOtherNode(BuildPromotion buildPromotion) {
    String creatorNodeId = buildPromotion.getCreatorNodeId();
    String currentNodeId = CurrentNodeInfo.getNodeId();
    return !creatorNodeId.equals(currentNodeId);
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    if (isQueueDisabled()) return;

    SBuildType buildType = getBuildType(Event.REMOVED_FROM_QUEUE, build);
    if (build instanceof QueuedBuildEx && ((QueuedBuildEx) build).isStarted()) return;
    if (isBuildFeatureAbsent(buildType)) return;

    if (isPublishingDisabled(build.getBuildType())) {
      LOG.info(String.format("Event: %s, build: %s: commit status publishing is disabled", Event.REMOVED_FROM_QUEUE, LogUtil.describe(build)));
      return;
    }

    if (user == null) { // build removal is processed only for manual remove build from the queue
      return;
    }

    BuildPromotion promotion = build.getBuildPromotion();
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

  @Override
  public void buildTypePersisted(@NotNull SBuildType buildType) {
    if (!myProblems.hasProblems(buildType)) {
      return;
    }
    clearObsoleteProblems(buildType);
  }

  @Override
  public void projectPersisted(@NotNull String projectId) {
    clearObsoleteProblemsForProject(projectId);
  }

  @Override
  public void projectRestored(@NotNull String projectId) {
    clearObsoleteProblemsForProject(projectId);
  }

  @Override
  public void buildTypeMoved(@NotNull SBuildType buildType, @NotNull SProject original) {
    if (!myProblems.hasProblems(buildType)) {
      return;
    }
    clearObsoleteProblems(buildType);
  }

  @Override
  public void buildTypeTemplatePersisted(@NotNull BuildTypeTemplate buildTemplate) {
    clearObsoleteProblems(buildTemplate);
  }

  @Used("tests")
  void setEventProcessedCallback(@Nullable Consumer<Event> callback) {
    myEventProcessedCallback = callback;
  }

  private void eventProcessed(Event event) {
    if (myEventProcessedCallback != null)
      myEventProcessedCallback.accept(event);
  }

  private boolean isBuildFeatureAbsent(@Nullable SBuildType buildType) {
    return buildType == null || buildType.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE).stream()
                                         .noneMatch(f -> buildType.isEnabled(f.getId()));
  }

  private boolean isPublishingDisabled(SBuildType buildType) {
    String publishingEnabledParam = buildType.getParameterValue(PUBLISHING_ENABLED_PROPERTY_NAME);
    return "false".equals(publishingEnabledParam)
           || !(TeamCityProperties.getBooleanOrTrue(PUBLISHING_ENABLED_PROPERTY_NAME)
                || "true".equals(publishingEnabledParam));
  }

  private void logStatusNotPublished(@NotNull Event event, @NotNull String buildDescription, @NotNull CommitStatusPublisher publisher, @NotNull String message) {
    LOG.info(String.format("Event: %s, build %s, publisher %s: %s", event.getName(), buildDescription, publisher, message));
  }

  private void submitTaskForBuild(@NotNull Event event, @NotNull SBuild build) {
    if  (!myServerResponsibility.isResponsibleForBuild(build)) {
      LOG.debug("Current node is not responsible for build " + LogUtil.describe(build) + ", skip processing event " + event);
      return;
    }

    long buildId = build.getBuildId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(event.getName(), event.getName() + ":" + buildId, buildId));
  }

  private boolean isCurrentRevisionSuitable(Event event, BuildPromotion buildPromotion, BuildRevision revision, CommitStatusPublisher publisher) throws PublisherException {
    if (TeamCityProperties.getBooleanOrTrue(CHECK_STATUS_BEFORE_PUBLISHING)) {
      RevisionStatus revisionStatus = publisher.getRevisionStatus(buildPromotion, revision);
      return revisionStatus == null || revisionStatus.isEventAllowed(event);
    }
    return true;
  }

  private void proccessRemovedFromQueueBuild(SQueuedBuild queuedBuild, User user, String comment) {
    BuildPromotion buildPromotion = queuedBuild.getBuildPromotion();
    AdditionalTaskInfo additionalTaskInfo = buildAdditionalRemovedFromQueueInfo(buildPromotion, comment, user);

    PublishingProcessor publishingProcessor = new PublishingProcessor() {
      @Override
      public void publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
        if (!publisher.isAvailable(buildPromotion))
          return;
        SBuildType buildType = buildPromotion.getBuildType();
        if (buildType == null) {
          return;
        }
        Lock lock = myPublishingLocks.get(getLockKey(buildType, revision));
        lock.lock();
        try {
          publisher.buildRemovedFromQueue(buildPromotion, revision, additionalTaskInfo);
        } catch (PublisherException e) {
          LOG.warn("Cannot publish removed build status to VCS for " + publisher.getBuildType() + ", commit: " + revision.getRevision(), e);
        } finally {
          lock.unlock();
        }
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
    LOG.debug("Event: " + event.getName() + ", build promotion " + LogUtil.describe(buildPromotion) + ", publishers: " + publishers.values());
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
      revisions.forEach(revision -> publishingProcessor.publish(event, revision, publisher));
    }
    myProblems.clearObsoleteProblems(buildType, publishers.keySet());
  }

  private AdditionalTaskInfo buildAdditionalRemovedFromQueueInfo(BuildPromotion buildPromotion, String comment, User user) {
    User actualCommentAuthor;
    if (comment != null) {
      actualCommentAuthor = user;
    } else {
      Pair<String, User> commentWithAuthor = getCommentWithAuthor(buildPromotion);
      actualCommentAuthor = commentWithAuthor.getSecond();
    }
    BuildPromotion replacingPromotion = myBuildPromotionManager.findPromotionOrReplacement(buildPromotion.getId());
    return new AdditionalTaskInfo(buildPromotion, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, actualCommentAuthor, (replacingPromotion == null || replacingPromotion.getId() == buildPromotion.getId()) ? null : replacingPromotion);
  }

  @Nullable
  private SBuildType getBuildType(@NotNull Event event, @NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      LOG.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build));
    return buildType;
  }

  @Nullable
  private SBuildType getBuildType(@NotNull Event event, @NotNull SQueuedBuild build) {
    try {
      return build.getBuildType();
    } catch (BuildTypeNotFoundException e) {
      LOG.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build));
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

  private void clearObsoleteProblemsForProject(@NotNull String projectId) {
    SProject project = myProjectManager.findProjectById(projectId);
    if (project == null) {
      return;
    }
    clearObsoleteProblems(getBuildTypesWithProblems(project.getOwnBuildTypes().stream()));
  }

  private void clearObsoleteProblems(@NotNull BuildTypeTemplate buildTemplate) {
    Collection<SBuildType> buildTypes = getBuildTypesWithProblems(buildTemplate.getUsages().stream());
    buildTypes.addAll(getBuildTypesWithProblems(buildTemplate.getUsagesAsDefaultTemplate()));
    if (buildTemplate instanceof BuildTypeTemplateEx) {
      buildTypes.addAll(getBuildTypesWithProblems(((BuildTypeTemplateEx) buildTemplate).getUsagesAsEnforcedSettings()));
    }
    clearObsoleteProblems(buildTypes);
  }

  private Collection<SBuildType> getBuildTypesWithProblems(@NotNull Stream<SBuildType> buildTypes) {
    return buildTypes.filter(myProblems::hasProblems)
                     .collect(Collectors.toSet());
  }

  private Collection<SBuildType> getBuildTypesWithProblems(@NotNull Collection<SProject> projects) {
    return getBuildTypesWithProblems(projects.stream()
                                             .map(SProject::getBuildTypes)
                                             .flatMap(Collection::stream));
  }

  private void clearObsoleteProblems(@NotNull Collection<SBuildType> buildTypes) {
    if (buildTypes.isEmpty()) {
      return;
    }
    runAsync(() -> {
      buildTypes.forEach(this::clearObsoleteProblems);
    }, null);
  }

  private void clearObsoleteProblems(@NotNull SBuildType buildType) {
    Set<String> activeBuildFeaturesIds = buildType.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE).stream()
                                                  .map(SBuildFeatureDescriptor::getId)
                                                  .filter(buildType::isEnabled)
                                                  .collect(Collectors.toSet());
    myProblems.clearObsoleteProblems(buildType, activeBuildFeaturesIds);
  }

  @NotNull
  private Collection<BuildRevision> getQueuedBuildRevisionForVote(@NotNull BuildType buildType,
                                                            @NotNull CommitStatusPublisher publisher,
                                                            @NotNull BuildPromotion buildPromotion) {
    if (buildPromotion.isFailedToCollectChanges()) return Collections.emptyList();

    if (!((BuildPromotionEx)buildPromotion).isChangeCollectingNeeded(false)) {
      return getBuildRevisionForVote(publisher, buildPromotion.getRevisions());
    }
    LOG.debug("No revision is found for build " + buildPromotion.getBuildTypeExternalId() + ". Queue-related status won't be published");
    return Collections.emptyList();
  }

  @NotNull
  private List<BuildRevision> getBuildRevisionForVote(@NotNull CommitStatusPublisher publisher,
                                                      @NotNull Collection<BuildRevision> revisionsToCheck) {
    if (revisionsToCheck.isEmpty()) return Collections.emptyList();

    String vcsRootId = publisher.getVcsRootId();
    if (vcsRootId == null) {
      List<BuildRevision> revisions = new ArrayList<BuildRevision>();
      for (BuildRevision revision : revisionsToCheck) {
        if (publisher.isPublishingForRevision(revision)) {
          revisions.add(revision);
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

  @Override
  public boolean shouldCollectChangesNow(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType == null) return false;

    return shouldCollectChangesNow(buildType);
  }

  @NotNull
  private Map<String, CommitStatusPublisher> getPublishers(@NotNull SBuildType buildType) {
    Map<String, CommitStatusPublisher> publishers = new LinkedHashMap<String, CommitStatusPublisher>();
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        String featureId = buildFeatureDescriptor.getId();
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildType, featureId, buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publishers.put(featureId, publisher);
      }
    }
    return publishers;
  }

  private interface PublishingProcessor {
    void publish(Event event, BuildRevision revision, CommitStatusPublisher publisher);
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

      runAsync(() -> runForEveryPublisher(eventType, build), () -> { eventProcessed(eventType); });
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

    private void runForEveryPublisher(@NotNull Event event, @NotNull SBuild build) {

      PublishTask task = myTaskSupplier.apply(build);

      SBuildType buildType = build.getBuildType();
      if (buildType == null)
        return;
      BuildPromotion buildPromotion = build.getBuildPromotion();

      PublishingProcessor publishingProcessor = new PublishingProcessor() {
        @Override
        public void publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
          Lock lock = myPublishingLocks.get(revision.getRevision());
          lock.lock();
          try {
            runTask(event, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision, null);
          } finally {
            lock.unlock();
          }
        }

        @Override
        public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
          if (buildPromotion.isFailedToCollectChanges()) return Collections.emptyList();
          return getBuildRevisionForVote(publisher, build.getRevisions());
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
    public boolean beforeAccept(@NotNull final PerformingTask task) {
      return myServerResponsibility.canManageBuilds();
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

      runAsync(() -> runForEveryPublisher(eventType, promotion, additionalTaskInfo), () -> { eventProcessed(eventType); });
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

    private void runForEveryPublisher(@NotNull Event event, @NotNull BuildPromotion buildPromotion, AdditionalTaskInfo additionalTaskInfo) {
      PublishQueuedTask publishTask = myTaskSupplier.apply(buildPromotion);

      PublishingProcessor publishingProcessor = new PublishingProcessor() {
        @Override
        public void publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
          runAsync(() -> {
            SBuildType buildType = buildPromotion.getBuildType();
            if (buildType == null) {
              return;
            }
            Lock lock = myPublishingLocks.get(revision.getRevision());
            lock.lock();
            try {
              doPublish(revision, publisher);
            } finally {
              lock.unlock();
            }
          }, null);
        }

        private void doPublish(BuildRevision revision, CommitStatusPublisher publisher) {
          boolean isEventSuitableForRevision;
          try {
            isEventSuitableForRevision = isCurrentRevisionSuitable(event, buildPromotion, revision, publisher);
          } catch (PublisherException e) {
            LOG.warn("Can not define if event \"" + event + "\" can be published for current revision state in VCS", e);
            return;
          }
          if (isEventSuitableForRevision) {
            runTask(event, buildPromotion, LogUtil.describe(buildPromotion), publishTask, publisher, revision, additionalTaskInfo);
          } else {
            LOG.debug("Event \"" + event + "\" is not suitable to be published to rooot \"" + publisher.getVcsRootId() + "\" for revision " + revision.getRevision());
          }
        }

        @Override
        public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
          return getQueuedBuildRevisionForVote(buildType, publisher, buildPromotion);
        }
      };
      proccessPublishing(event, buildPromotion, publishingProcessor);
    }

  }

  private abstract class PublisherTaskConsumer<T> extends MultiNodeTasks.TaskConsumer {

    abstract void doRunTask(T task, CommitStatusPublisher publisher, BuildRevision revision, AdditionalTaskInfo additionalTaskInfo) throws PublisherException;

    @Nullable
    protected Event getEventType(PerformingTask task) {
      String taskType = task.getType();
      return myEventTypes.get(taskType);
    }

    protected void runTask(@NotNull Event event,
                           @NotNull BuildPromotion promotion,
                           @NotNull String buildDescription,
                           @NotNull T publishTask,
                           @NotNull CommitStatusPublisher publisher,
                           @NotNull BuildRevision revision,
                           @Nullable AdditionalTaskInfo additionalTaskInfo) {
      try {
        if (!publisher.isAvailable(promotion)) {
          return;
        }
        doRunTask(publishTask, publisher, revision, additionalTaskInfo);
      } catch (Throwable t) {
        myProblems.reportProblem(String.format("Commit Status Publisher has failed to publish %s status", event.getName()), publisher, buildDescription, null, t, LOG);
        if (shouldFailBuild(publisher.getBuildType())) {
          String problemId = "commitStatusPublisher." + publisher.getId() + "." + revision.getRoot().getId();
          String problemDescription = t instanceof PublisherException ? t.getMessage() : t.toString();
          BuildProblemData buildProblem = BuildProblemData.createBuildProblem(problemId, "commitStatusPublisherProblem", problemDescription);
          ((BuildPromotionEx)promotion).addBuildProblem(buildProblem);
        }
      }
    }
  }
}
