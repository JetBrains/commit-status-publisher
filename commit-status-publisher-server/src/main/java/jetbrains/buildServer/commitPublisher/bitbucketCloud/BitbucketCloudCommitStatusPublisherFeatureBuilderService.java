package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider;
import org.jetbrains.annotations.NotNull;

public class BitbucketCloudCommitStatusPublisherFeatureBuilderService implements CommitStatusPublisherFeatureBuilderService {
  @Override
  public boolean supportsVcsHostingType(@NotNull String vcsHostingType) {
    return BitBucketOAuthProvider.TYPE.equals(vcsHostingType);
  }

  @NotNull
  @Override
  public String getPublisherId() {
    return Constants.BITBUCKET_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public CommitStatusPublisherFeatureBuilder createFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    return new BitbucketCloudCommitStatusPublisherFeatureBuilder(settings);
  }
}
