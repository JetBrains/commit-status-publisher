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
  public void changesLoaded(SRunningBuild build) {
    if (build == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      publisher.buildStarted(build);
    }
  }

  @Override
  public void buildFinished(SRunningBuild build) {
    if (build == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null)
      return;

    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      publisher.buildFinished(finishedBuild);
    }
  }

  @Override
  public void buildCommented(@NotNull SBuild build, @Nullable User user, @Nullable String comment) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      publisher.buildCommented(build, user, comment);
    }
  }

  @Override
  public void buildInterrupted(SRunningBuild build) {
    if (build == null)
      return;

    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    SFinishedBuild finishedBuild = myBuildHistory.findEntry(build.getBuildId());
    if (finishedBuild == null)
      return;

    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      publisher.buildInterrupted(finishedBuild);
    }
  }


  @Override
  public void buildChangedStatus(SRunningBuild build, Status oldStatus, Status newStatus) {
    if (build == null)
      return;

    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    for (CommitStatusPublisher publisher : getPublishers(buildType)) {
      publisher.buildFailureDetected(build);
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
}
