package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommitStatusPublisherListener extends BuildServerAdapter {

  private final static Logger LOG = Logger.getInstance(CommitStatusPublisherListener.class.getName());

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
    String event = "changesLoaded";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildStarted(build, revision);
      }
    });
  }

  @Override
  public void buildFinished(@NotNull SRunningBuild build) {
    String event = "buildFinished";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug("Event: " + event + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildFinished(finishedBuild, revision);
      }
    });
  }

  @Override
  public void buildCommented(@NotNull final SBuild build, @Nullable final User user, @Nullable final String comment) {
    String event = "buildCommented";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;
    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildCommented(build, revision, user, comment, isBuildInProgress(build));
      }
    });
  }

  @Override
  public void buildInterrupted(@NotNull SRunningBuild build) {
    String event = "buildInterrupted";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null) {
      LOG.debug("Event: " + event + ", cannot find finished build for build " + LogUtil.describe(build));
      return;
    }

    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildInterrupted(finishedBuild, revision);
      }
    });
  }


  @Override
  public void buildChangedStatus(@NotNull final SRunningBuild build, Status oldStatus, Status newStatus) {
    String event = "buildChangedStatus";

    if (oldStatus.isFailed() || !newStatus.isFailed()) // we are supposed to report failures only
      return;

    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildFailureDetected(build, revision);
      }
    });
  }


  @Override
  public void buildProblemsChanged(@NotNull final SBuild build, @NotNull final List<BuildProblemData> before, @NotNull final List<BuildProblemData> after) {
    String event = "buildProblemsChanged";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    if (!before.isEmpty() && after.isEmpty()) {
      runForEveryPublisher(event, buildType, build, new PublishTask()  {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
          return publisher.buildMarkedAsSuccessful(build, revision, isBuildInProgress(build));
        }
      });
    }
  }

  @Override
  public void buildTypeAddedToQueue(@NotNull final SQueuedBuild build) {
    String event = "buildTypeAddedToQueue";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    runForEveryPublisherQueued(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildQueued(build, revision);
      }
    });
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    String event = "buildRemovedFromQueue";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    if (user == null)
      return;

    runForEveryPublisherQueued(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) throws PublisherException {
        return publisher.buildRemovedFromQueue(build, revision, user, comment);
      }
    });
  }

  private void runForEveryPublisher(@NotNull String event, @NotNull SBuildType buildType, @NotNull SBuild build, @NotNull PublishTask task) {
    if (build.isPersonal()) // we do not publish commit statuses for personal builds at all for now
      return;
    Map<String, CommitStatusPublisher> publishers = getPublishers(buildType);
    LOG.debug("Event: " + event + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values());
    for (Map.Entry<String, CommitStatusPublisher> pubEntry : publishers.entrySet()) {
      CommitStatusPublisher publisher = pubEntry.getValue();
      BuildRevision revision = getBuildRevisionForVote(event, publisher, build);
      if (revision == null) {
        LOG.info("Event: " + event + ", build " + LogUtil.describe(build) + ", cannot find revision for publisher " +
                publisher + ", skip it");
        continue;
      }
      runTask(event, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision);
    }
    myProblems.clearObsoleteProblems(buildType, publishers.keySet());
  }

  private void runForEveryPublisherQueued(@NotNull String event, @NotNull SBuildType buildType, @NotNull SQueuedBuild build, @NotNull PublishTask task) {
    Map<String, CommitStatusPublisher> publishers = getPublishers(buildType);
    LOG.debug("Event: " + event + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values());
    for (Map.Entry<String, CommitStatusPublisher> pubEntry : publishers.entrySet()) {
      CommitStatusPublisher publisher = pubEntry.getValue();
      BuildRevision revision = getQueuedBuildRevisionForVote(event, buildType, publisher, build);
      if (revision == null) {
        LOG.info("Event: " + event + ", build " + LogUtil.describe(build) + ", cannot find revision for publisher " +
                publisher + ", skip it");
        continue;
      }
      runTask(event, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision);
    }
    myProblems.clearObsoleteProblems(buildType, publishers.keySet());
  }

  private void runTask(@NotNull String event,
                       @NotNull BuildPromotion promotion,
                       @NotNull String buildDescription,
                       @NotNull PublishTask task,
                       @NotNull CommitStatusPublisher publisher,
                       @NotNull BuildRevision revision) {
    try {
      if (task.run(publisher, revision))
        myProblems.clearProblem(publisher);
    } catch (Throwable t) {
      myProblems.reportProblem(String.format("Commit Status Publisher has failed to publish %s status", event), publisher, buildDescription, null, t, LOG);
      if (shouldFailBuild(publisher.getBuildType())) {
        String problemId = "commitStatusPublisher." + publisher.getId() + "." + revision.getRoot().getId();
        String problemDescription = t instanceof PublisherException ? t.getMessage() : t.toString();
        BuildProblemData buildProblem = BuildProblemData.createBuildProblem(problemId, "commitStatusPublisherProblem", problemDescription);
        ((BuildPromotionEx)promotion).addBuildProblem(buildProblem);
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

  @Nullable
  private BuildRevision getBuildRevisionForVote(@NotNull String event, @NotNull CommitStatusPublisher publisher, @NotNull SBuild build) {

    if (build.getBuildPromotion().isFailedToCollectChanges()) return null;

    String vcsRootId = publisher.getVcsRootId();
    if (vcsRootId == null) {
      LOG.info("Event: " + event + ", build " + LogUtil.describe(build) + ", publisher " + publisher +
              ": VCS root is not specified");
      return null;
    }

    try {
      long parentRootId = Long.valueOf(vcsRootId);
      return findRevisionByInternalId(build, parentRootId);
    } catch (NumberFormatException e) {
      // external id
      for (BuildRevision revision : build.getRevisions()) {
        if (vcsRootId.equals(revision.getRoot().getParent().getExternalId()))
          return revision;
      }
    }

    return null;
  }

  @Nullable
  private BuildRevision findRevisionByInternalId(@NotNull SBuild build, long vcsRootId) {
    for (BuildRevision revision : build.getRevisions()) {
      if (vcsRootId == revision.getRoot().getParentId())
        return revision;
    }

    return null;
  }

  @Nullable
  private BuildRevision getQueuedBuildRevisionForVote(@NotNull String event,
                                                      @NotNull SBuildType buildType,
                                                      @NotNull CommitStatusPublisher publisher,
                                                      @NotNull SQueuedBuild build) {
    BuildPromotion p = build.getBuildPromotion();
    SBuild b = p.getAssociatedBuild();
    if (b != null) {
      BuildRevision revision = getBuildRevisionForVote(event, publisher, b);
      if (revision != null)
        return revision;
    }

    String branchName = getBranchName(p);
    BranchEx branch = ((BuildTypeEx) buildType).getBranch(branchName);
    return getBuildRevisionForVote(event, publisher, branch.getDummyBuild());
  }

  @NotNull
  private String getBranchName(@NotNull BuildPromotion p) {
    Branch b = p.getBranch();
    if (b == null)
      return Branch.DEFAULT_BRANCH_NAME;
    return b.getName();
  }

  @Nullable
  private SBuildType getBuildType(@NotNull String event, @NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      LOG.debug("Event: " + event + ", cannot find buildType for build " + LogUtil.describe(build));
    return buildType;
  }

  @Nullable
  private SBuildType getBuildType(@NotNull String event, @NotNull SQueuedBuild build) {
    try {
      return build.getBuildType();
    } catch (BuildTypeNotFoundException e) {
      LOG.debug("Event: " + event + ", cannot find buildType for build " + LogUtil.describe(build));
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
}
