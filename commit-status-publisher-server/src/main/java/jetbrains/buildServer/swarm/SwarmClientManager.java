package jetbrains.buildServer.swarm;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwarmClientManager {
  @Nullable
  public SwarmClient getSwarmClient(@NotNull SBuildType buildType, @NotNull VcsRootInstance vcsRoot) {
    //Collection<SBuildFeatureDescriptor> publisherFeatures = buildType.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE);
    //
    //buildType.getBuildFeaturesOfType()
    return null;
  }
}
