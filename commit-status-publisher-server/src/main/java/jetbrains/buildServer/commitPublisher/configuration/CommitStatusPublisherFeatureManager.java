package jetbrains.buildServer.commitPublisher.configuration;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.tfs.AzureDevOpsCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.connections.ConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcshostings.VcsHostingTypeProvider;
import jetbrains.buildServer.vcshostings.features.AbstractVcsHostingBuildFeatureManager;
import jetbrains.buildServer.vcshostings.features.VcsHostingBuildFeature;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class CommitStatusPublisherFeatureManager extends AbstractVcsHostingBuildFeatureManager {
  @NotNull
  private final CommitStatusPublisherFeatureBuilderFactory myFactory;

  public CommitStatusPublisherFeatureManager(@NotNull VcsHostingTypeProvider vcsHostingTypeProvider, @NotNull CommitStatusPublisherFeatureBuilderFactory factory) {
    super(vcsHostingTypeProvider);
    myFactory = factory;
  }

  @Override
  public String getBuildFeatureType() {
    return CommitStatusPublisherFeature.TYPE;
  }

  @Override
  public SBuildFeatureDescriptor configureMinimalBuildFeature(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot) {
    try {
      String vcsHostingType = getVcsHostingType(buildType, vcsRoot);
      if (vcsHostingType == null) {
        LOG.debug(() -> "Failed to determine hosting type for VCS root " + LogUtil.describe(vcsRoot) + ", of build type " + LogUtil.describe(buildType));
        return null;
      }

      CommitStatusPublisherFeatureBuilder builder = myFactory.createForProviderType(vcsHostingType);
      builder.withVcsRootAuthentication();
      builder.withVcsRoot(vcsRoot);

      if (builder instanceof AzureDevOpsCommitStatusPublisherFeatureBuilder) {
        builder.withPublishPullRequestStatuses(true);
      }

      VcsHostingBuildFeature feature = builder.build(buildType);
      return buildType.addBuildFeature(feature.getType(), feature.getParameters());
    } catch (Exception e) {
      LOG.infoAndDebugDetails("Failed to configure a minimal build feature with exception", e);
      return null;
    }
  }

  @Override
  public boolean isBuildFeatureSupported(@NotNull ConnectionDescriptor connection) {
    try {
      if (connection instanceof OAuthConnectionDescriptor) {
        myFactory.createForConnection((OAuthConnectionDescriptor)connection);
        return true;
      }
      return false;
    } catch (IllegalArgumentException e) {
      LOG.debug(() -> "Build feature is not supported for connection " + LogUtil.describe(connection), e);
      return false;
    } catch (IllegalStateException e) {
      LOG.debug(() -> "Build feature is not supported for connection " + LogUtil.describe(connection), e);
      return false;
    }
  }

  @Override
  public boolean isBuildFeatureSupported(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot) {
    try {
      String vcsHostingType = getVcsHostingType(buildType, vcsRoot);
      if (vcsHostingType == null) {
        LOG.debug(() -> "Build feature is not supported for VCS root " + LogUtil.describe(vcsRoot) + ", of build type " + LogUtil.describe(buildType));
        return false;
      }

      myFactory.createForProviderType(vcsHostingType);
      return true;
    } catch (Exception e) {
      LOG.debug(() -> "Build feature is not supported for VCS root " + LogUtil.describe(vcsRoot) + ", of build type " + LogUtil.describe(buildType));
      return false;
    }
  }
}
