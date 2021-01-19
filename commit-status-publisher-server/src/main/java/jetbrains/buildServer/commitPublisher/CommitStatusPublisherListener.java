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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.MultiNodeTasks.PerformingTask;
import jetbrains.buildServer.serverSide.comments.Comment;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

import static jetbrains.buildServer.commitPublisher.CommitStatusPublisher.LOG;

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
  private final Map<String, Event> myEventTypes = new HashMap<>();
  private static final Striped<Lock> myLocks = Striped.lazyWeakLock(100);
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
                                       @NotNull MultiNodeTasks multiNodeTasks) {
    myPublisherManager = voterManager;
    myBuildHistory = buildHistory;
    myBuildsManager = buildsManager;
    myBuildPromotionManager = buildPromotionManager;
    myProblems = problems;
    myServerResponsibility = serverResponsibility;
    myMultiNodeTasks = multiNodeTasks;
    myExecutorServices = executorServices;
    myEventTypes.putAll(Arrays.stream(Event.values()).collect(Collectors.toMap(Event::getName, et -> et)));

    events.addListener(this);

    myMultiNodeTasks.subscribe(Event.STARTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          return publisher.buildStarted(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.FINISHED.getName(), new BuildPublisherTaskConsumer (
       build -> new PublishTask() {
         @Override
         public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
           return publisher.buildFinished(build, revision);
         }
       }
    ));

    myMultiNodeTasks.subscribe(Event.MARKED_AS_SUCCESSFUL.getName(), new BuildPublisherTaskConsumer (
       build -> new PublishTask() {
         @Override
         public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
           return publisher.buildMarkedAsSuccessful(build, revision, isBuildInProgress(build));
         }
        }
    ));

    myMultiNodeTasks.subscribe(Event.COMMENTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          Comment comment = build.getBuildComment();
          if (null == comment)
            return true;
          return publisher.buildCommented(build, revision, comment.getUser(), comment.getComment(), isBuildInProgress(build));
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.INTERRUPTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          return publisher.buildInterrupted(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.FAILURE_DETECTED.getName(), new BuildPublisherTaskConsumer (
      build -> new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          return publisher.buildFailureDetected(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.QUEUED.getName(), new QueuedBuildPublisherTaskConsumer(
      build -> new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          return publisher.buildQueued(build, revision);
        }
      }
    ));

    myMultiNodeTasks.subscribe(Event.REMOVED_FROM_QUEUE.getName(), new QueuedBuildPublisherTaskConsumer(
      build -> new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          Comment comment = build.getBuildPromotion().getBuildComment();
          return publisher.buildRemovedFromQueue(build, revision, comment == null ? null : comment.getUser(), comment == null ? null : comment.getComment());
        }
      }
    ));
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

    submitTaskForQueuedBuild(Event.QUEUED, build);
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    SBuildType buildType = getBuildType(Event.REMOVED_FROM_QUEUE, build);
    if (isBuildFeatureAbsent(buildType))
      return;

    submitTaskForQueuedBuild(Event.REMOVED_FROM_QUEUE, build);
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
    if (build.isPersonal()) {
      for(SVcsModification change: build.getBuildPromotion().getPersonalChanges()) {
        if (change.isPersonal())
          return;
      }
    }

    long buildId = build.getBuildId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(event.getName(), event.getName() + ":" + buildId, buildId, null, null));
  }

  private void submitTaskForQueuedBuild(@NotNull Event event, @NotNull SQueuedBuild build) {
    if  (!myServerResponsibility.canManageBuilds()) {
      LOG.debug("Current node is not responsible for build " + LogUtil.describe(build) + ", skip processing event " + event);
      return;
    }
    if (build.isPersonal()) {
      for(SVcsModification change: build.getBuildPromotion().getPersonalChanges()) {
        if (change.isPersonal())
          return;
      }
    }
    long promotionId = build.getBuildPromotion().getId();
    myMultiNodeTasks.submit(new MultiNodeTasks.TaskData(event.getName(), event.getName() + ":" + promotionId, promotionId, null, null));
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
    boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException;
  }

  private boolean isBuildInProgress(SBuild build) {
    return myBuildsManager.findRunningBuildById(build.getBuildId()) != null;
  }

  private boolean shouldFailBuild(@NotNull SBuildType buildType) {
    return Boolean.parseBoolean(buildType.getParameters().get("teamcity.commitStatusPublisher.failBuildOnPublishError"));
  }

  private class BuildPublisherTaskConsumer extends PublisherTaskConsumer {

    private final Function<SBuild, PublishTask> myTaskSupplier;

    BuildPublisherTaskConsumer(Function<SBuild, PublishTask> taskSupplier) {
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

      CompletableFuture.runAsync(() -> {
        Lock lock = myLocks.get(build.getBuildTypeId());
        lock.lock();
        try {
          runForEveryPublisher(eventType, build);
        } finally {
          lock.unlock();
        }
      }, myExecutorServices.getLowPriorityExecutorService()).handle((r, t) -> {
        task.finished();
        eventProcessed(eventType);
        return r;
      });
    }

    @Nullable
    private SBuild getBuild(final PerformingTask task) {
      Long buildId = task.getLongArg1();
      if (buildId == null) return null;

      return myBuildsManager.findBuildInstanceById(buildId);
    }



    private void runForEveryPublisher(@NotNull Event event, @NotNull SBuild build) {

      PublishTask task = myTaskSupplier.apply(build);

      SBuildType buildType = build.getBuildType();
      if (buildType == null)
        return;
      Map<String, CommitStatusPublisher> publishers = getPublishers(buildType);
      LOG.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values());
      for (Map.Entry<String, CommitStatusPublisher> pubEntry : publishers.entrySet()) {
        CommitStatusPublisher publisher = pubEntry.getValue();
        if (!publisher.isEventSupported(event))
          continue;
        if (isPublishingDisabled(buildType)) {
          logStatusNotPublished(event, LogUtil.describe(build), publisher, "commit status publishing is disabled");
          continue;
        }
        List<BuildRevision> revisions = getBuildRevisionForVote(publisher, build);
        if (revisions.isEmpty()) {
          logStatusNotPublished(event, LogUtil.describe(build), publisher, "no compatible revisions found");
          continue;
        }
        myProblems.clearProblem(publisher);
        for (BuildRevision revision: revisions) {
          runTask(event, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision);
        }
      }
      myProblems.clearObsoleteProblems(buildType, publishers.keySet());
    }

  }

  private class QueuedBuildPublisherTaskConsumer extends PublisherTaskConsumer {

    private final Function<SQueuedBuild, PublishTask> myTaskSupplier;

    QueuedBuildPublisherTaskConsumer(Function<SQueuedBuild, PublishTask> taskSupplier) {
      myTaskSupplier = taskSupplier;
    }

    @Override
    public boolean beforeAccept(@NotNull final PerformingTask task) {
      return myServerResponsibility.canManageBuilds();
    }

    @Override
    public void accept(final PerformingTask task) {
      Event eventType = getEventType(task);
      SQueuedBuild build = getBuild(task);
      if (eventType == null || build == null) {
        task.finished();
        eventProcessed(eventType);
        return;
      }
      CompletableFuture.runAsync(() -> {
        Lock lock = myLocks.get(build.getBuildTypeId());
        lock.lock();
        try {
          runForEveryPublisher(eventType, build);
        } finally {
          lock.unlock();
        }
      }, myExecutorServices.getLowPriorityExecutorService()).handle((r, t) -> {
        task.finished();
        eventProcessed(eventType);
        return r;
      });
    }

    @Nullable
    private SQueuedBuild getBuild(final PerformingTask task) {
      Long promotionId = task.getLongArg1();
      if (promotionId == null)
        return null;

      BuildPromotion promotion = myBuildPromotionManager.findPromotionById(promotionId);
      if (promotion == null)
        return null;

      return promotion.getQueuedBuild();
    }

    private void runForEveryPublisher(@NotNull Event event, @NotNull SQueuedBuild build) {
      PublishTask publishTask = myTaskSupplier.apply(build);
      SBuildType buildType = build.getBuildType();

      Map<String, CommitStatusPublisher> publishers = getPublishers(buildType);
      LOG.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values());
      for (Map.Entry<String, CommitStatusPublisher> pubEntry : publishers.entrySet()) {
        CommitStatusPublisher publisher = pubEntry.getValue();
        if (!publisher.isEventSupported(event))
          continue;
        if (isPublishingDisabled(buildType)) {
          logStatusNotPublished(event, LogUtil.describe(build), publisher, "commit status publishing is disabled");
          continue;
        }
        List<BuildRevision> revisions = getQueuedBuildRevisionForVote(buildType, publisher, build);
        if (revisions.isEmpty()) {
          logStatusNotPublished(event, LogUtil.describe(build), publisher, "no compatible revisions found");
          continue;
        }
        myProblems.clearProblem(publisher);
        for (BuildRevision revision: revisions) {
          runTask(event, build.getBuildPromotion(), LogUtil.describe(build), publishTask, publisher, revision);
        }
      }
      myProblems.clearObsoleteProblems(buildType, publishers.keySet());
    }

  }


  private abstract class PublisherTaskConsumer extends MultiNodeTasks.TaskConsumer {

    @Nullable
    protected Event getEventType(PerformingTask task) {
      String taskType = task.getType();
      return myEventTypes.get(taskType);
    }

    protected void runTask(@NotNull Event event,
                           @NotNull BuildPromotion promotion,
                           @NotNull String buildDescription,
                           @NotNull PublishTask publishTask,
                           @NotNull CommitStatusPublisher publisher,
                           @NotNull BuildRevision revision) {
      try {
        publishTask.run(publisher, revision);
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

    @NotNull
    protected Map<String, CommitStatusPublisher> getPublishers(@NotNull SBuildType buildType) {
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

    @NotNull
    protected List<BuildRevision> getBuildRevisionForVote(@NotNull CommitStatusPublisher publisher, @NotNull SBuild build) {

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
        SVcsRoot root = revision.getRoot().getParent();
        if (vcsRootId.equals(root.getExternalId()) || vcsRootId.equals(String.valueOf(root.getId())))
          return Arrays.asList(revision);
      }

      return Collections.emptyList();
    }

    @NotNull
    protected List<BuildRevision> getQueuedBuildRevisionForVote(@NotNull SBuildType buildType,
                                                              @NotNull CommitStatusPublisher publisher,
                                                              @NotNull SQueuedBuild build) {
      BuildPromotion p = build.getBuildPromotion();
      SBuild b = p.getAssociatedBuild();
      if (b != null) {
        List<BuildRevision> revisions = getBuildRevisionForVote(publisher, b);
        if (!revisions.isEmpty())
          return revisions;
      }

      String branchName = getBranchName(p);
      BranchEx branch = ((BuildTypeEx) buildType).getBranch(branchName);
      return getBuildRevisionForVote(publisher, branch.getDummyBuild());
    }

  }
}
