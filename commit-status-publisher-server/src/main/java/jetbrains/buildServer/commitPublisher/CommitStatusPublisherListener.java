package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;

import java.util.*;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

public class CommitStatusPublisherListener extends BuildServerAdapter {

  private final static Logger LOG = Logger.getInstance(CommitStatusPublisherListener.class.getName());
  private final static String PUBLISHING_ENABLED_PROPERTY_NAME = "teamcity.commitStatusPublisher.enabled";
  private final static String PUBLISHING_TO_DEPENDENCIES_ENABLED_PROPERTY_NAME = "teamcity.commitStatusPublisher.publishToDependencies";

  private final PublisherManager myPublisherManager;
  private final BuildHistory myBuildHistory;
  private final RunningBuildsManager myRunningBuilds;
  private final CommitStatusPublisherProblems myProblems;

  public CommitStatusPublisherListener(@NotNull EventDispatcher<BuildServerListener> events,
                                       @NotNull PublisherManager voterManager,
                                       @NotNull BuildHistory buildHistory,
                                       @NotNull RunningBuildsManager runningBuilds,
                                       @NotNull CommitStatusPublisherProblems problems) {
    myPublisherManager = voterManager;
    myBuildHistory = buildHistory;
    myRunningBuilds = runningBuilds;
    myProblems = problems;
    events.addListener(this);
  }

  @Override
  public void changesLoaded(@NotNull final SRunningBuild build) {
    SBuildType buildType = getBuildType(Event.STARTED, build);
    if (buildType == null)
      return;

    runForEveryPublisher(Event.STARTED, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildStarted(build, revision);
      }
    });
  }

  @Override
  public void buildFinished(@NotNull SRunningBuild build) {
    SBuildType buildType = getBuildType(Event.FINISHED, build);
    if (buildType == null)
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug("Event: " + Event.FINISHED + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    runForEveryPublisher(Event.FINISHED, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildFinished(finishedBuild, revision);
      }
    });
  }

  @Override
  public void buildCommented(@NotNull final SBuild build, @Nullable final User user, @Nullable final String comment) {
    SBuildType buildType = getBuildType(Event.COMMENTED, build);
    if (buildType == null)
      return;
    runForEveryPublisher(Event.COMMENTED, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildCommented(build, revision, user, comment, isBuildInProgress(build));
      }
    });
  }

  @Override
  public void buildInterrupted(@NotNull SRunningBuild build) {
    SBuildType buildType = getBuildType(Event.INTERRUPTED, build);
    if (buildType == null)
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug("Event: " + Event.INTERRUPTED.getName() + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    runForEveryPublisher(Event.INTERRUPTED, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildInterrupted(finishedBuild, revision);
      }
    });
  }


  @Override
  public void buildChangedStatus(@NotNull final SRunningBuild build, Status oldStatus, Status newStatus) {
    if (oldStatus.isFailed() || !newStatus.isFailed()) // we are supposed to report failures only
      return;

    SBuildType buildType = getBuildType(Event.FAILURE_DETECTED, build);
    if (buildType == null)
      return;

    runForEveryPublisher(Event.FAILURE_DETECTED, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildFailureDetected(build, revision);
      }
    });
  }


  @Override
  public void buildProblemsChanged(@NotNull final SBuild build, @NotNull final List<BuildProblemData> before, @NotNull final List<BuildProblemData> after) {
    SBuildType buildType = getBuildType(Event.MARKED_AS_SUCCESSFUL, build);
    if (buildType == null)
      return;

    if (!before.isEmpty() && after.isEmpty()) {
      runForEveryPublisher(Event.MARKED_AS_SUCCESSFUL, buildType, build, new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          return publisher.buildMarkedAsSuccessful(build, revision, isBuildInProgress(build));
        }
      });
    }
  }

  @Override
  public void buildTypeAddedToQueue(@NotNull final SQueuedBuild build) {
    SBuildType buildType = getBuildType(Event.QUEUED, build);
    if (buildType == null)
      return;

    runForEveryPublisherQueued(Event.QUEUED, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildQueued(build, revision);
      }
    });
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    SBuildType buildType = getBuildType(Event.REMOVED_FROM_QUEUE, build);
    if (buildType == null)
      return;

    if (user == null)
      return;

    runForEveryPublisherQueued(Event.REMOVED_FROM_QUEUE, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildRemovedFromQueue(build, revision, user, comment);
      }
    });
  }

  private boolean isPublishingDisabled(SBuildType buildType) {
    String publishingEnabledParam = buildType.getParameterValue(PUBLISHING_ENABLED_PROPERTY_NAME);
    return "false".equals(publishingEnabledParam)
      || !(TeamCityProperties.getBooleanOrTrue(PUBLISHING_ENABLED_PROPERTY_NAME)
      || "true".equals(publishingEnabledParam));
  }

  private boolean isPublishingToDependenciesEnabled(SBuildType buildType) {
    String publishingEnabledParam = buildType.getParameterValue(PUBLISHING_TO_DEPENDENCIES_ENABLED_PROPERTY_NAME);
    return StringUtil.areEqualIgnoringCase("true", publishingEnabledParam);
  }

  private void logStatusNotPublished(@NotNull Event event, @NotNull String buildDescription, @NotNull CommitStatusPublisher publisher, @NotNull String message) {
    LOG.info(String.format("Event: %s, build %s, publisher %s: %s", event.getName(), buildDescription, publisher.toString(), message));
  }

  private void runForEveryPublisher(@NotNull Event event, @NotNull SBuildType buildType, @NotNull SBuild build, @NotNull PublishTask task) {
    final String description = LogUtil.describe(build);
    DoubleKeyHashSet<String, BuildRevision> publishedRevisionsByPublisher = new DoubleKeyHashSet<String, BuildRevision>();
    runForEveryPublisher(event, buildType, build, task, description, publishedRevisionsByPublisher);

    if (!isPublishingToDependenciesEnabled(buildType)) {
      return;
    }

    for (BuildPromotion bp : build.getBuildPromotion().getAllDependencies()) {
      final SBuild associatedBuild = bp.getAssociatedBuild();
      if (associatedBuild != null) {
        runForEveryPublisher(event, bp.getBuildType(), associatedBuild, task, description, publishedRevisionsByPublisher);
      }
    }

  }

  private void runForEveryPublisher(@NotNull Event event, @NotNull SBuildType buildType, @NotNull SBuild build, @NotNull PublishTask task, @NotNull String description, @NotNull DoubleKeyHashSet<String, BuildRevision> publishedRevisionsByPublisher) {
    if (build.isPersonal()) {
      for (SVcsModification change : build.getBuildPromotion().getPersonalChanges()) {
        if (change.isPersonal())
          return;
      }
    }
    Map<String, CommitStatusPublisher> publishers = getPublishers(buildType);
    LOG.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values() + ", description: " + description);
    for (Map.Entry<String, CommitStatusPublisher> pubEntry : publishers.entrySet()) {
      CommitStatusPublisher publisher = pubEntry.getValue();
      if (!publisher.isEventSupported(event))
        continue;
      if (isPublishingDisabled(buildType)) {
        logStatusNotPublished(event, description, publisher, "commit status publishing is disabled");
        continue;
      }
      List<BuildRevision> revisions = getBuildRevisionForVote(publisher, build);
      if (revisions.isEmpty()) {
        logStatusNotPublished(event, description, publisher, "no compatible revisions found");
        continue;
      }
      myProblems.clearProblem(publisher);
      for (BuildRevision revision : revisions) {
        if (!publishedRevisionsByPublisher.contains(pubEntry.getKey(), revision)) {
          runTask(event, build.getBuildPromotion(), description, task, publisher, revision);
          publishedRevisionsByPublisher.add(pubEntry.getKey(), revision);
        }

      }
    }
    myProblems.clearObsoleteProblems(buildType, publishers.keySet());
  }

  private void runForEveryPublisherQueued(@NotNull Event event, @NotNull SBuildType buildType, @NotNull SQueuedBuild build, @NotNull PublishTask task) {
    final String description = LogUtil.describe(build);
    DoubleKeyHashSet<String, BuildRevision> publishedRevisionsByPublisher = new DoubleKeyHashSet<String, BuildRevision>();
    runForEveryPublisherQueued(event, buildType, build, task, description, publishedRevisionsByPublisher);

    if (!isPublishingToDependenciesEnabled(buildType)) {
      return;
    }

    for (BuildPromotion bp : build.getBuildPromotion().getAllDependencies()) {
      final SQueuedBuild queuedBuild = bp.getQueuedBuild();
      final SBuild associatedBuild = bp.getAssociatedBuild();
      if (associatedBuild != null) {
        runForEveryPublisher(event, bp.getBuildType(), associatedBuild, task, description, publishedRevisionsByPublisher);
      } else if (queuedBuild != null) {
        runForEveryPublisherQueued(event, bp.getBuildType(), queuedBuild, task, description, publishedRevisionsByPublisher);
      }
    }
  }

  private void runForEveryPublisherQueued(@NotNull Event event, @NotNull SBuildType buildType, @NotNull SQueuedBuild build, @NotNull PublishTask task, @NotNull String description, @NotNull DoubleKeyHashSet<String, BuildRevision> publishedRevisionsByPublisher) {
    if (build.isPersonal()) {
      for (SVcsModification change : build.getBuildPromotion().getPersonalChanges()) {
        if (change.isPersonal())
          return;
      }
    }
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
      for (BuildRevision revision : revisions) {
        if (!publishedRevisionsByPublisher.contains(pubEntry.getKey(), revision)) {
          runTask(event, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision);
          publishedRevisionsByPublisher.add(pubEntry.getKey(), revision);
        }
      }
    }
    myProblems.clearObsoleteProblems(buildType, publishers.keySet());
  }

  private void runTask(@NotNull Event event,
                       @NotNull BuildPromotion promotion,
                       @NotNull String buildDescription,
                       @NotNull PublishTask task,
                       @NotNull CommitStatusPublisher publisher,
                       @NotNull BuildRevision revision) {
    try {
      task.run(publisher, revision);
    } catch (Throwable t) {
      myProblems.reportProblem(String.format("Commit Status Publisher has failed to publish %s status", event.getName()), publisher, buildDescription, null, t, LOG);
      if (shouldFailBuild(publisher.getBuildType())) {
        String problemId = "commitStatusPublisher." + publisher.getId() + "." + revision.getRoot().getId();
        String problemDescription = t instanceof PublisherException ? t.getMessage() : t.toString();
        BuildProblemData buildProblem = BuildProblemData.createBuildProblem(problemId, "commitStatusPublisherProblem", problemDescription);
        ((BuildPromotionEx) promotion).addBuildProblem(buildProblem);
      }
    }
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
      SVcsRoot root = revision.getRoot().getParent();
      if (vcsRootId.equals(root.getExternalId()) || vcsRootId.equals(String.valueOf(root.getId())))
        return Arrays.asList(revision);
    }

    return Collections.emptyList();
  }

  @NotNull
  private List<BuildRevision> getQueuedBuildRevisionForVote(@NotNull SBuildType buildType,
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

  @NotNull
  private String getBranchName(@NotNull BuildPromotion p) {
    Branch b = p.getBranch();
    if (b == null)
      return Branch.DEFAULT_BRANCH_NAME;
    return b.getName();
  }

  @Nullable
  private SBuildType getBuildType(@NotNull CommitStatusPublisher.Event event, @NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      LOG.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build));
    return buildType;
  }

  @Nullable
  private SBuildType getBuildType(@NotNull CommitStatusPublisher.Event event, @NotNull SQueuedBuild build) {
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
    return myRunningBuilds.findRunningBuildById(build.getBuildId()) != null;
  }

  private boolean shouldFailBuild(@NotNull SBuildType buildType) {
    return Boolean.valueOf(buildType.getParameters().get("teamcity.commitStatusPublisher.failBuildOnPublishError"));
  }

  private class DoubleKeyHashSet<Key1, Key2> {
    private final Map<Key1, Set<Key2>> myMap = new HashMap<Key1, Set<Key2>>();

    public boolean contains(Key1 key1, Key2 key2) {
      Set<Key2> key2Set = myMap.get(key1);
      return (key2Set != null ? key2Set : Collections.<Key2>emptySet()).contains(key2);
    }

    public void add(Key1 key1, Key2 key2) {
      Set<Key2> key2Set = myMap.get(key1);
      if (key2Set == null) {
        key2Set = new HashSet<Key2>();
        myMap.put(key1, key2Set);
      }
      key2Set.add(key2);
    }
  }
}
