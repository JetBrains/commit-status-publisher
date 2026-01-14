package jetbrains.buildServer.commitPublisher.gitlab;

import com.google.common.collect.ImmutableSet;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabCEorEEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabComOAuthProvider;
import org.jetbrains.annotations.NotNull;

public class GitLabCommitStatusPublisherFeatureBuilderService implements CommitStatusPublisherFeatureBuilderService {
  private static final ImmutableSet<String> SUPPORTED_VCS_HOSTINGS = ImmutableSet.of(GitLabComOAuthProvider.TYPE, GitLabCEorEEOAuthProvider.TYPE);

  @Override
  public boolean supportsVcsHostingType(@NotNull String vcsHostingType) {
    return SUPPORTED_VCS_HOSTINGS.contains(vcsHostingType);
  }

  @NotNull
  @Override
  public String getPublisherId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public CommitStatusPublisherFeatureBuilder createFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    return new GitLabCommitStatusPublisherFeatureBuilder(settings);
  }
}
