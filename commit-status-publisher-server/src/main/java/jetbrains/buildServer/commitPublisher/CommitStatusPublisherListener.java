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

import java.util.ArrayList;
import java.util.List;

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
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
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
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
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

    final boolean inProgress = myRunningBuilds.findRunningBuildById(build.getBuildId()) != null;

    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        return publisher.buildCommented(build, revision, user, comment, inProgress);
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
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        return publisher.buildInterrupted(finishedBuild, revision);
      }
    });
  }


  @Override
  public void buildChangedStatus(@NotNull final SRunningBuild build, Status oldStatus, Status newStatus) {
    String event = "buildChangedStatus";
    SBuildType buildType = getBuildType(event, build);
    if (buildType == null)
      return;

    runForEveryPublisher(event, buildType, build, new PublishTask() {
      @Override
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
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
      runForEveryPublisher(event, buildType, build, new PublishTask() {
        @Override
        public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
          return publisher.buildMarkedAsSuccessful(build, revision);
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
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
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
      public boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        return publisher.buildRemovedFromQueue(build, revision, user, comment);
      }
    });
  }

  private void runForEveryPublisher(@NotNull String event, @NotNull SBuildType buildType, @NotNull SBuild build, @NotNull PublishTask task) {
    List<CommitStatusPublisher> publishers = getPublishers(buildType);
    LOG.debug("Event: " + event + ", build " + LogUtil.describe(build) + ", publishers: " + publishers);
    for (CommitStatusPublisher publisher : publishers) {
      BuildRevision revision = getBuildRevisionForVote(event, publisher, build);
      if (revision == null) {
        LOG.info("Event: " + event + ", build " + LogUtil.describe(build) + ", cannot find revision for publisher " +
                publisher + ", skip it");
        continue;
      }
      runTask(event, buildType, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision);
    }
  }

  private void runForEveryPublisherQueued(@NotNull String event, @NotNull SBuildType buildType, @NotNull SQueuedBuild build, @NotNull PublishTask task) {
    List<CommitStatusPublisher> publishers = getPublishers(buildType);
    LOG.debug("Event: " + event + ", build " + LogUtil.describe(build) + ", publishers: " + publishers);
    for (CommitStatusPublisher publisher : publishers) {
      BuildRevision revision = getQueuedBuildRevisionForVote(event, buildType, publisher, build);
      if (revision == null) {
        LOG.info("Event: " + event + ", build " + LogUtil.describe(build) + ", cannot find revision for publisher " +
                publisher + ", skip it");
        continue;
      }
      runTask(event, buildType, build.getBuildPromotion(), LogUtil.describe(build), task, publisher, revision);
    }
  }

  private void runTask(@NotNull String event,
                       @NotNull SBuildType buildType,
                       @NotNull BuildPromotion promotion,
                       @NotNull String buildDescription,
                       @NotNull PublishTask task,
                       @NotNull CommitStatusPublisher publisher,
                       @NotNull BuildRevision revision) {
    try {
      if (task.run(publisher, revision))
        myProblems.clearProblem(buildType, publisher);
    } catch (Throwable t) {
      LOG.warnAndDebugDetails("Error while running publisher " + publisher + ", event " + event + ", build " + buildDescription + ", revision " + revision, t);
      reportProblem(buildType, publisher, buildDescription, t);
      if (shouldFailBuild(buildType)) {
        String problemId = "commitStatusPublisher." + publisher.getId() + "." + revision.getRoot().getId();
        String problemDescription = t instanceof PublishError ? t.getMessage() : t.toString();
        BuildProblemData buildProblem = BuildProblemData.createBuildProblem(problemId, "commitStatusPublisherProblem", problemDescription);
        ((BuildPromotionEx)promotion).addBuildProblem(buildProblem);
      }
    }
  }

  @NotNull
  private List<CommitStatusPublisher> getPublishers(@NotNull SBuildType buildType) {
    List<CommitStatusPublisher> publishers = new ArrayList<CommitStatusPublisher>();
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publishers.add(publisher);
      }
    }
    return publishers;
  }

  @Nullable
  private BuildRevision getBuildRevisionForVote(@NotNull String event, @NotNull CommitStatusPublisher publisher, @NotNull SBuild build) {
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
    boolean run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision);
  }

  private void reportProblem(@NotNull SBuildType buildType,
                             @NotNull CommitStatusPublisher publisher,
                             @NotNull String buildDescription,
                             @NotNull Throwable t) {
    String description = "Publish status error in build " + buildDescription + ": ";
    if (t instanceof PublishError) {
      description += t.getMessage();
    } else {
      description += t.toString();
    }
    myProblems.reportProblem(buildType, publisher, description);
  }


  private boolean shouldFailBuild(@NotNull SBuildType buildType) {
    return Boolean.valueOf(buildType.getParameters().get("teamcity.commitStatusPublisher.failBuildOnPublishError"));
  }
}
