package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderFactory;
import org.jetbrains.annotations.NotNull;

/**
 * A builder for commit status publisher feature settings for GitLab.
 *
 * @see CommitStatusPublisherFeatureBuilderFactory#createForGitLab()
 * @since 2025.07
 */
public class GitLabCommitStatusPublisherFeatureBuilder extends CommitStatusPublisherFeatureBuilder {

  public GitLabCommitStatusPublisherFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    super(settings);
  }

  @NotNull
  @Override
  public GitLabCommitStatusPublisherFeatureBuilder withUrl(@NotNull String url) {
    withParameter(Constants.GITLAB_API_URL, url);
    return this;
  }

  @NotNull
  @Override
  public GitLabCommitStatusPublisherFeatureBuilder withPersonalToken(@NotNull String token) {
    clearAuthParameters();
    putParameter(Constants.AUTH_TYPE, Constants.AUTH_TYPE_ACCESS_TOKEN);
    putParameter(Constants.GITLAB_TOKEN, token);
    return this;
  }

  @Override
  protected void clearAuthParameters() {
    super.clearAuthParameters();
    clearParameter(Constants.GITLAB_TOKEN);
  }
}
