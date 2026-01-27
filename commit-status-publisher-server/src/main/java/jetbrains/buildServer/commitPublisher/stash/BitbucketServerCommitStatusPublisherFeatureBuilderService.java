package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilderService;
import org.jetbrains.annotations.NotNull;

public class BitbucketServerCommitStatusPublisherFeatureBuilderService implements CommitStatusPublisherFeatureBuilderService {
  @Override
  public boolean supportsVcsHostingType(@NotNull String vcsHostingType) {
    return Constants.STASH_OAUTH_PROVIDER_TYPE.equals(vcsHostingType);
  }

  @NotNull
  @Override
  public String getPublisherId() {
    return Constants.STASH_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public CommitStatusPublisherFeatureBuilder createFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    return new  BitbucketServerCommitStatusPublisherFeatureBuilder(settings);
  }
}
