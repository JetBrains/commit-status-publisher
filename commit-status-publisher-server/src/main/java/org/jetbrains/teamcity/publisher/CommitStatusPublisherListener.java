package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CommitStatusPublisherListener extends BuildServerAdapter {

  private final PublisherManager myPublisherManager;
  private final BuildHistory myBuildHistory;

  public CommitStatusPublisherListener(@NotNull EventDispatcher<BuildServerListener> events,
                                       @NotNull PublisherManager voterManager,
                                       @NotNull BuildHistory buildHistory) {
    myPublisherManager = voterManager;
    myBuildHistory = buildHistory;
    events.addListener(this);
  }

  @Override
  public void changesLoaded(final SRunningBuild build) {
    if (build == null)
      return;

    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    runForEveryPublisher(buildType, build, new PublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildStarted(build, revision);
      }
    });
  }

  @Override
  public void buildFinished(SRunningBuild build) {
    if (build == null)
      return;

    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null)
      return;

    runForEveryPublisher(buildType, build, new PublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildFinished(finishedBuild, revision);
      }
    });
  }

  @Override
  public void buildCommented(@NotNull final SBuild build, @Nullable final User user, @Nullable final String comment) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    runForEveryPublisher(buildType, build, new PublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildCommented(build, revision, user, comment);
      }
    });
  }

  @Override
  public void buildInterrupted(final SRunningBuild build) {
    if (build == null)
      return;

    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    final SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null)
      return;

    runForEveryPublisher(buildType, build, new PublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildInterrupted(finishedBuild, revision);
      }
    });
  }


  @Override
  public void buildChangedStatus(final SRunningBuild build, Status oldStatus, Status newStatus) {
    if (build == null)
      return;

    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    runForEveryPublisher(buildType, build, new PublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildFailureDetected(build, revision);
      }
    });
  }


  @Override
  public void buildTypeAddedToQueue(@NotNull final SQueuedBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    runForEveryPublisher(buildType, build, new QueuedBuildPublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildQueued(build, revision);
      }
    });
  }

  @Override
  public void buildRemovedFromQueue(@NotNull final SQueuedBuild build, final User user, final String comment) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    if (user == null)
      return;

    runForEveryPublisher(buildType, build, new QueuedBuildPublishTask() {
      public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision) {
        publisher.buildRemovedFromQueue(build, revision, user, comment);
      }
    });
  }

  private void runForEveryPublisher(@NotNull SBuildType buildType, @NotNull SBuild build, @NotNull PublishTask task) {
    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      BuildRevision revision = getBuildRevisionForVote(publisher, build);
      if (revision == null)
        continue;
      task.run(publisher, revision);
    }
  }

  private void runForEveryPublisher(@NotNull SBuildType buildType, @NotNull SQueuedBuild build, @NotNull QueuedBuildPublishTask task) {
    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      BuildRevision revision = getQueuedBuildRevisionForVote(buildType, publisher, build);
      if (revision == null)
        continue;
      task.run(publisher, revision);
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
  private BuildRevision getBuildRevisionForVote(@NotNull CommitStatusPublisher publisher, @NotNull SBuild build) {
    String vcsRootId = publisher.getVcsRootId();
    if (vcsRootId == null)
      return null;
    long parentRootId = Long.valueOf(vcsRootId);
    for (BuildRevision revision : build.getRevisions()) {
      if (parentRootId == revision.getRoot().getParentId())
        return revision;
    }
    return null;
  }

  @Nullable
  private BuildRevision getQueuedBuildRevisionForVote(@NotNull SBuildType buildType,
                                                      @NotNull CommitStatusPublisher publisher,
                                                      @NotNull SQueuedBuild build) {
    BuildPromotion p = build.getBuildPromotion();
    SBuild b = p.getAssociatedBuild();
    if (b != null) {
      BuildRevision revision = getBuildRevisionForVote(publisher, b);
      if (revision != null)
        return revision;
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

  private static interface PublishTask {
    public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision);
  }

  private static interface QueuedBuildPublishTask {
    public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision);
  }
}
