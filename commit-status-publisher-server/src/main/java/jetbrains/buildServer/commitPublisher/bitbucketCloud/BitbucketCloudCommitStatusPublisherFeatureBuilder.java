package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderFactory;
import org.jetbrains.annotations.NotNull;

/**
 * A builder for commit status publisher feature settings for Bitbucket Cloud.
 *
 * @see CommitStatusPublisherFeatureBuilderFactory#createForBitbucketCloud()
 * @since 2025.07
 */
public class BitbucketCloudCommitStatusPublisherFeatureBuilder extends CommitStatusPublisherFeatureBuilder {

  public BitbucketCloudCommitStatusPublisherFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    super(settings);
  }

  @NotNull
  @Override
  public BitbucketCloudCommitStatusPublisherFeatureBuilder withPassword(@NotNull String username, @NotNull String password) {
    clearAuthParameters();
    putParameter(authTypeParameterName(), Constants.PASSWORD);
    putParameter(Constants.BITBUCKET_CLOUD_USERNAME, username);
    putParameter(Constants.BITBUCKET_CLOUD_PASSWORD, password);
    return this;
  }

  @Override
  protected void clearAuthParameters() {
    super.clearAuthParameters();
    clearParameter(Constants.BITBUCKET_CLOUD_USERNAME);
    clearParameter(Constants.BITBUCKET_CLOUD_PASSWORD);
  }
}
