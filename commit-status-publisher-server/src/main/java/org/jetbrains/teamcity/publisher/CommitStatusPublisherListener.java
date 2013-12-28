package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

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
