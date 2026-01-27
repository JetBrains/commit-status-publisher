package jetbrains.buildServer.commitPublisher.tfs;

import com.google.common.collect.ImmutableSet;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.serverSide.oauth.azuredevops.AzureDevOpsOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.tfs.TfsAuthProvider;
import org.jetbrains.annotations.NotNull;

public class AzureDevOpsCommitStatusPublisherFeatureBuilderService implements CommitStatusPublisherFeatureBuilderService {
  private static final ImmutableSet<String> SUPPORTED_VCS_HOSTINGS = ImmutableSet.of(AzureDevOpsOAuthProvider.TYPE, TfsAuthProvider.TYPE);

  @Override
  public boolean supportsVcsHostingType(@NotNull String vcsHostingType) {
    return SUPPORTED_VCS_HOSTINGS.contains(vcsHostingType);
  }

  @NotNull
  @Override
  public String getPublisherId() {
    return TfsConstants.ID;
  }

  @NotNull
  @Override
  public CommitStatusPublisherFeatureBuilder createFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    return new AzureDevOpsCommitStatusPublisherFeatureBuilder(settings);
  }
}
