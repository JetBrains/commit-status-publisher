package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public class CommitStatusPublisherListener extends BuildServerAdapter {

  private final PublisherManager myPublisherManager;

  public CommitStatusPublisherListener(@NotNull EventDispatcher<BuildServerListener> events,
                                       @NotNull PublisherManager voterManager) {
    myPublisherManager = voterManager;
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

    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        CommitStatusPublisher publisher = myPublisherManager.createPublisher(buildFeatureDescriptor.getParameters());
        if (publisher != null)
          publisher.buildFinished(build);
      }
    }
  }
}
