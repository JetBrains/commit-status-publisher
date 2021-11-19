/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

public class CommitStatusPublisherListener extends BuildServerAdapter {

  private final static String PUBLISHING_ENABLED_PROPERTY_NAME = "teamcity.commitStatusPublisher.enabled";
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
  private final UserModel myUserModel;
  private final Map<String, Event> myEventTypes = new HashMap<>();
  private final Striped<Lock> myLocks = Striped.lazyWeakLock(100);
  private final Map<Long, Event> myLastEvents =
    new LinkedHashMap<Long, Event> () {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Long, Event> eldest)
      {
        return size() > MAX_LAST_EVENTS_TO_REMEMBER;
      }
    };

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
                                       @NotNull UserModel userModel,
                                       @NotNull MultiNodeTasks multiNodeTasks) {
    myPublisherManager = voterManager;
    myBuildHistory = buildHistory;
    myBuildsManager = buildsManager;
    myBuildPromotionManager = buildPromotionManager;
    myProblems = problems;
    myServerResponsibility = serverResponsibility;
    myMultiNodeTasks = multiNodeTasks;
    myExecutorServices = executorServices;
    myProjectManager = projectManager;
    myUserModel = userModel;
    myEventTypes.putAll(Arrays.stream(Event.values()).collect(Collectors.toMap(Event::getName, et -> et)));

    events.addListener(this);

    myMultiNodeTasks.subscribe(Event.STARTED.getName(), new BuildPublisherTaskConsumer (
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

    myMultiNodeTasks.subscribe(Event.QUEUED.getName(), new QueuedBuildPublisherTaskConsumer(
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
  public void changesLoaded(@NotNull final SRunningBuild build) {
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

  @Override
  public void buildTypeAddedToQueue(@NotNull final SQueuedBuild build) {
    SBuildType buildType = getBuildType(Event.QUEUED, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    if  (!myServerResponsibility.canManageBuilds()) {
      LOG.debug("Current node is not responsible for build " + LogUtil.describe(build) + ", skip processing event " + Event.QUEUED);
      return;
    }
    long promotionId = build.getBuildPromotion().getId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(Event.QUEUED.getName(), Event.QUEUED.getName() + ":" + promotionId, promotionId, null, DefaultStatusMessages.BUILD_QUEUED));
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    SBuildType buildType = getBuildType(Event.REMOVED_FROM_QUEUE, build);
    if (build instanceof QueuedBuildEx && ((QueuedBuildEx) build).isStarted()) return;
    if (isBuildFeatureAbsent(buildType)) return;

    if  (!myServerResponsibility.canManageBuilds()) {
      LOG.debug("Current node is not responsible for build " + LogUtil.describe(build) + ", skip processing removing from queue");
      return;
    }
    if (isPublishingDisabled(build.getBuildType())) {
      LOG.info(String.format("Event: %s, build: %s: commit status publishing is disabled", Event.REMOVED_FROM_QUEUE, LogUtil.describe(build)));
      return;
    }

    runAsync(() -> {
      proccessRemovedFromQueueBuild(build, user, comment);
    }, null);
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
    LOG.info(String.format("Event: %s, build %s, publisher %s: %s", event.getName(), buildDescription, publisher.toString(), message));
  }

  private void submitTaskForBuild(@NotNull Event event, @NotNull SBuild build) {
    if  (!myServerResponsibility.isResponsibleForBuild(build)) {
      LOG.debug("Current node is not responsible for build " + LogUtil.describe(build) + ", skip processing event " + event);
      return;
    }

    long buildId = build.getBuildId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(event.getName(), event.getName() + ":" + buildId, buildId, null, null));
  }

  private void proccessRemovedFromQueueBuild(SQueuedBuild queuedBuild, User user, String comment) {
    BuildPromotion buildPromotion = queuedBuild.getBuildPromotion();
    AdditionalTaskInfo additionalTaskInfo = buildAdditionalRemovedFromQueueInfo(buildPromotion, comment, user);

    PublishingProcessor publishingProcessor = new PublishingProcessor() {
      @Override
      public void publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
        if (!publisher.isAvailable(buildPromotion))
          return;
        try {
          publisher.buildRemovedFromQueue(buildPromotion, revision, additionalTaskInfo);
        } catch (PublisherException e) {
          LOG.warn("Cannot publish removed build status to VCS for " + publisher.getBuildType() + ", commit: " + revision.getRevision(), e);
        }
      }

      @Override
      public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
        return getQueuedBuildRevisionForVote(buildType, publisher, buildPromotion);
      }
    };
    proccessPublishing(Event.REMOVED_FROM_QUEUE, buildPromotion, publishingProcessor);
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

  private AdditionalRemovedFromQueueInfo buildAdditionalRemovedFromQueueInfo(BuildPromotion buildPromotion, String comment, User user) {
    String actualComment;
    User actualCommentAuthor;
    if (comment != null) {
      actualComment = comment;
      actualCommentAuthor = user;
    } else {
      Pair<String, User> commentWithAuthor = getCommentWithAuthor(buildPromotion);
      actualComment = commentWithAuthor.getFirst();
      actualCommentAuthor = commentWithAuthor.getSecond();
    }
    BuildPromotion replacingPromotion = myBuildPromotionManager.findPromotionOrReplacement(buildPromotion.getId());
    return new AdditionalRemovedFromQueueInfo(actualComment, actualCommentAuthor, (replacingPromotion == null || replacingPromotion.getId() == buildPromotion.getId()) ? null : replacingPromotion);
  }

  @NotNull
  private String getBranchName(@NotNull BuildPromotion p) {
    Branch b = p.getBranch();
    if (b == null)
      return Branch.DEFAULT_BRANCH_NAME;
    return b.getName();
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
  private List<BuildRevision> getQueuedBuildRevisionForVote(@NotNull BuildType buildType,
                                                            @NotNull CommitStatusPublisher publisher,
                                                            @NotNull BuildPromotion buildPromotion) {
    SBuild b = buildPromotion.getAssociatedBuild();
    if (b != null) {
      List<BuildRevision> revisions = getBuildRevisionForVote(publisher, b);
      if (!revisions.isEmpty())
        return revisions;
    }

    String branchName = getBranchName(buildPromotion);
    BranchEx branch = ((BuildTypeEx) buildType).getBranch(branchName);
    return getBuildRevisionForVote(publisher, branch.getDummyBuild());
  }

  @NotNull
  private List<BuildRevision> getBuildRevisionForVote(@NotNull CommitStatusPublisher publisher, @NotNull SBuild build) {
    if (build.getBuildPromotion().isFailedToCollectChanges()) return Collections.emptyList();

    String vcsRootId = publisher.getVcsRootId();
    if (vcsRootId == null) {
      List<BuildRevision> revisions = new ArrayList<BuildRevision>();
      for (BuildRevision revision : build.getRevisions()) {
        if (publisher.isPublishingForRevision(revision)) {
          revisions.add(revision);
        }
      }
      return revisions;
    }

    for (BuildRevision revision : build.getRevisions()) {
      SVcsRootEx root = (SVcsRootEx)revision.getRoot().getParent();
      if (vcsRootId.equals(root.getExternalId()) || root.isAliasExternalId(vcsRootId) || vcsRootId.equals(String.valueOf(root.getId())))
        return Arrays.asList(revision);
    }

    return Collections.emptyList();
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

  private class BuildPublisherTaskConsumer extends PublisherTaskConsumer<PublishTask, AdditionalTaskInfo> {

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

      if (eventType == null || build == null) {
        task.finished();
        eventProcessed(eventType);
        return;
      }

      synchronized (myLastEvents) {
        if (myLastEvents.get(build.getBuildId()) != null && eventType.isFirstTask()) {
          task.finished();
          eventProcessed(eventType);
          return;
        }
        if (eventType.isConsequentTask())
          myLastEvents.put(build.getBuildId(), eventType);
      }

      // We are accepting the task. It will be either completed or will fail
      // One way or another it will be marked as finished (see TW-69618)
      task.finished();

      runAsync(() -> {
          Lock lock = myLocks.get(build.getBuildTypeId());
          lock.lock();
          try {
            runForEveryPublisher(eventType, build);
          } finally {
            lock.unlock();
          }
        }, () -> { eventProcessed(eventType); });
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
          runTask(event, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision, null);
        }

        @Override
        public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
          return getBuildRevisionForVote(publisher, build);
        }
      };

      proccessPublishing(event, buildPromotion, publishingProcessor);
    }

  }

  private class QueuedBuildPublisherTaskConsumer extends PublisherTaskConsumer<PublishQueuedTask, AdditionalQueuedInfo> {

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

      User commentAuthor = getUser(task);
      String comment = getComment(task);
      if (eventType == null || promotion == null) {
        task.finished();
        eventProcessed(eventType);
        return;
      }

      task.finished();

      AdditionalQueuedInfo additionalTaskInfo = new AdditionalQueuedInfo(comment, commentAuthor);

      runAsync(() -> {
        Lock lock = myLocks.get(promotion.getBuildTypeId());
        lock.lock();
        try {
          runForEveryPublisher(eventType, promotion, additionalTaskInfo);
        } finally {
          lock.unlock();
        }
      }, () -> { eventProcessed(eventType); });
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
    void doRunTask(PublishQueuedTask task, CommitStatusPublisher publisher, BuildRevision revision, AdditionalQueuedInfo additionalTaskInfo) throws PublisherException {
      task.run(publisher, revision, additionalTaskInfo);
    }

    private void runForEveryPublisher(@NotNull Event event, @NotNull BuildPromotion buildPromotion, AdditionalQueuedInfo additionalTaskInfo) {
      PublishQueuedTask publishTask = myTaskSupplier.apply(buildPromotion);

      PublishingProcessor publishingProcessor = new PublishingProcessor() {
        @Override
        public void publish(Event event, BuildRevision revision, CommitStatusPublisher publisher) {
          runTask(event, buildPromotion, LogUtil.describe(buildPromotion), publishTask, publisher, revision, additionalTaskInfo);
        }

        @Override
        public Collection<BuildRevision> getRevisions(BuildType buildType, CommitStatusPublisher publisher) {
          return getQueuedBuildRevisionForVote(buildType, publisher, buildPromotion);
        }
      };
      proccessPublishing(event, buildPromotion, publishingProcessor);
    }

  }


  private abstract class PublisherTaskConsumer<T, I extends AdditionalTaskInfo> extends MultiNodeTasks.TaskConsumer {

    abstract void doRunTask(T task, CommitStatusPublisher publisher, BuildRevision revision, I additionalTaskInfo) throws PublisherException;

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
                           @Nullable I additionalTaskInfo) {
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
