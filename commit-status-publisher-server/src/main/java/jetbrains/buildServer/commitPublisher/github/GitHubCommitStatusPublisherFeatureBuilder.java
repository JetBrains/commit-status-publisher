package jetbrains.buildServer.commitPublisher.github;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderFactory;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import org.jetbrains.annotations.NotNull;

/**
 * A builder for commit status publisher feature settings for GitHub.
 *
 * @see CommitStatusPublisherFeatureBuilderFactory#createForGitHub()
 * @since 2025.07
 */
public class GitHubCommitStatusPublisherFeatureBuilder extends CommitStatusPublisherFeatureBuilder {

  private static final Set<String> AUTH_PARAMETERS = ImmutableSet.of(
    Constants.AUTH_TYPE,
    Constants.GITHUB_TOKEN,
    Constants.GITHUB_USERNAME,
    Constants.GITHUB_PASSWORD,
    Constants.TOKEN_ID
  );

  public GitHubCommitStatusPublisherFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    super(settings);
    putParameter(Constants.GITHUB_SERVER, GitHubApiFactory.DEFAULT_URL);
  }

  @NotNull
  @Override
  public GitHubCommitStatusPublisherFeatureBuilder withUrl(@NotNull String url) {
    putParameter(Constants.GITHUB_SERVER, url);
    return this;
  }

  @NotNull
  @Override
  public GitHubCommitStatusPublisherFeatureBuilder withPersonalToken(@NotNull String token) {
    clearAuthParameters();
    putParameter(Constants.GITHUB_AUTH_TYPE, Constants.AUTH_TYPE_ACCESS_TOKEN);
    putParameter(Constants.GITHUB_TOKEN, token);
    return this;
  }

  @NotNull
  @Override
  public GitHubCommitStatusPublisherFeatureBuilder withPassword(@NotNull String username, @NotNull String password) {
    clearAuthParameters();
    putParameter(Constants.GITHUB_AUTH_TYPE, Constants.PASSWORD);
    putParameter(Constants.GITHUB_USERNAME, username);
    putParameter(Constants.GITHUB_PASSWORD, password);
    return this;
  }

  @Override
  protected void clearAuthParameters() {
    super.clearAuthParameters();
    clearParameters(AUTH_PARAMETERS);
  }

  @NotNull
  @Override
  protected String authTypeParameterName() {
    return Constants.GITHUB_AUTH_TYPE;
  }
}
