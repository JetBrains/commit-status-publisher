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

  private void runForEveryPublisher(@NotNull SBuildType buildType, @NotNull SBuild build, @NotNull PublishTask task) {
    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      BuildRevision revision = getBuildRevisionForVote(publisher, build);
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

  private static interface PublishTask {
    public void run(@NotNull CommitStatusPublisher publisher, @NotNull BuildRevision revision);
  }
}
