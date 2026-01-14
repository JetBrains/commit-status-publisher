package jetbrains.buildServer.commitPublisher.github;

import com.google.common.collect.ImmutableSet;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.serverSide.oauth.github.GHEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.github.GitHubOAuthProvider;
import org.jetbrains.annotations.NotNull;

public class GitHubCommitStatusPublisherFeatureBuilderService implements CommitStatusPublisherFeatureBuilderService {
  private static final ImmutableSet<String> SUPPORTED_VCS_HOSTINGS = ImmutableSet.of(GitHubOAuthProvider.TYPE, GHEOAuthProvider.TYPE, "GitHubApp");

  @Override
  public boolean supportsVcsHostingType(@NotNull String vcsHostingType) {
    return SUPPORTED_VCS_HOSTINGS.contains(vcsHostingType);
  }

  @NotNull
  @Override
  public String getPublisherId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public CommitStatusPublisherFeatureBuilder createFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    return new GitHubCommitStatusPublisherFeatureBuilder(settings);
  }
}
