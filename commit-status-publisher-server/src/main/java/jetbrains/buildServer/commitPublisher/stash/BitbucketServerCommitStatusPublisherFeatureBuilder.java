package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import org.jetbrains.annotations.NotNull;

public class BitbucketServerCommitStatusPublisherFeatureBuilder extends CommitStatusPublisherFeatureBuilder {
  protected BitbucketServerCommitStatusPublisherFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    super(settings);
  }

  @NotNull
  @Override
  public BitbucketServerCommitStatusPublisherFeatureBuilder withUrl(@NotNull String url) {
    putParameter(Constants.STASH_BASE_URL, url);
    return this;
  }

  @NotNull
  @Override
  public BitbucketServerCommitStatusPublisherFeatureBuilder withPassword(@NotNull String username, @NotNull String password) {
    clearAuthParameters();
    putParameter(authTypeParameterName(), Constants.PASSWORD);
    putParameter(Constants.STASH_USERNAME, username);
    putParameter(Constants.STASH_PASSWORD, password);
    return this;
  }

  @Override
  protected void clearAuthParameters() {
    super.clearAuthParameters();
    clearParameter(Constants.STASH_USERNAME);
    clearParameter(Constants.STASH_PASSWORD);
  }
}
