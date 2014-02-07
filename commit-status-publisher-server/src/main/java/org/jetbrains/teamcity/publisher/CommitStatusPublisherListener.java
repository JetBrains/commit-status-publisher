package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public void buildTypeAddedToQueue(@NotNull SQueuedBuild queuedBuild) {
    SBuildType buildType = queuedBuild.getBuildType();
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildQueued(queuedBuild);
      }
    }
  }

  @Override
  public void buildRemovedFromQueue(@NotNull SQueuedBuild queuedBuild, User user, String comment) {
    if (user == null)
      return;
    SBuildType buildType = queuedBuild.getBuildType();
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildRemovedFromQueue(queuedBuild, user, comment);
      }
    }
  }

  @Override
  public void buildInterrupted(SRunningBuild build) {
    if (build == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildInterrupted(build);
      }
    }
  }

  @Override
  public void buildChangedStatus(SRunningBuild build, Status oldStatus, Status newStatus) {
    if (build == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildChangedStatus(build, oldStatus, newStatus);
      }
    }
  }

  @Override
  public void changesLoaded(SRunningBuild build) {
    if (build == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildStarted(build);
      }
    }
  }

  @Override
  public void buildCommented(@NotNull SBuild build, @Nullable User user, @Nullable String comment) {
    if (user == null)
      return;
    if (comment == null || comment.trim().length() == 0)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;

    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildCommented(build, user, comment);
      }
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

    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildFinished(finishedBuild);
      }
    }
  }
}
