package jetbrains.buildServer.commitPublisher.configuration;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import jetbrains.buildServer.ExtensionsCollection;
import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.BitbucketCloudCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.github.GitHubCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.gitlab.GitLabCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.github.GHEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.github.GitHubOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabCEorEEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabComOAuthProvider;
import org.jetbrains.annotations.NotNull;

public class CommitStatusPublisherFeatureBuilderFactory {

  private static final Map<String, PublisherIdAndBuilderConstructor> CONNECTION_TYPE_TO_PUBLISHER_ID_AND_CONSTRUCTOR = ImmutableMap.of(
    GitHubOAuthProvider.TYPE, new PublisherIdAndBuilderConstructor(Constants.GITHUB_PUBLISHER_ID, GitHubCommitStatusPublisherFeatureBuilder::new),
    GHEOAuthProvider.TYPE, new PublisherIdAndBuilderConstructor(Constants.GITHUB_PUBLISHER_ID, GitHubCommitStatusPublisherFeatureBuilder::new),
    BitBucketOAuthProvider.TYPE, new PublisherIdAndBuilderConstructor(Constants.BITBUCKET_PUBLISHER_ID, BitbucketCloudCommitStatusPublisherFeatureBuilder::new),
    GitLabComOAuthProvider.TYPE, new PublisherIdAndBuilderConstructor(Constants.GITLAB_PUBLISHER_ID, GitLabCommitStatusPublisherFeatureBuilder::new),
    GitLabCEorEEOAuthProvider.TYPE, new PublisherIdAndBuilderConstructor(Constants.GITLAB_PUBLISHER_ID, GitLabCommitStatusPublisherFeatureBuilder::new)
  );

  private final ExtensionsCollection<CommitStatusPublisherSettings> mySettings;

  public CommitStatusPublisherFeatureBuilderFactory(@NotNull ExtensionsProvider extensionsProvider) {
    mySettings = extensionsProvider.getExtensionsCollection(CommitStatusPublisherSettings.class);
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder createForConnection(@NotNull OAuthConnectionDescriptor connection) {
    final String oauthProviderType = connection.getProviderType();
    final PublisherIdAndBuilderConstructor publisherIdAndBuilderConstructor = CONNECTION_TYPE_TO_PUBLISHER_ID_AND_CONSTRUCTOR.get(oauthProviderType);
    if (publisherIdAndBuilderConstructor == null) {
      throw new IllegalArgumentException("Unsupported OAuth connection type: " + oauthProviderType);
    }

    return createBuilder(publisherIdAndBuilderConstructor.getPublisherId(), publisherIdAndBuilderConstructor.getConstructorFun());
  }

  private CommitStatusPublisherFeatureBuilder createBuilder(@NotNull String publisherId,
                                                            @NotNull Function<CommitStatusPublisherSettings, CommitStatusPublisherFeatureBuilder> constructorFun) {

    final Optional<CommitStatusPublisherSettings> maybeSettings = mySettings.getExtensions().stream()
                                                                            .filter(s -> publisherId.equals(s.getId()))
                                                                            .findFirst();

    if (!maybeSettings.isPresent()) {
      throw new IllegalStateException("publisher settings extension bean not found for publisher " + publisherId);
    }

    return constructorFun.apply(maybeSettings.get());
  }

  private static class PublisherIdAndBuilderConstructor {

    @NotNull
    private final String myPublisherId;

    @NotNull
    private final Function<CommitStatusPublisherSettings, CommitStatusPublisherFeatureBuilder> myConstructorFun;

    private PublisherIdAndBuilderConstructor(@NotNull String publisherId, @NotNull Function<CommitStatusPublisherSettings, CommitStatusPublisherFeatureBuilder> constructorFun) {
      myPublisherId = publisherId;
      myConstructorFun = constructorFun;
    }

    @NotNull
    private String getPublisherId() {
      return myPublisherId;
    }

    @NotNull
    private Function<CommitStatusPublisherSettings, CommitStatusPublisherFeatureBuilder> getConstructorFun() {
      return myConstructorFun;
    }
  }

}
