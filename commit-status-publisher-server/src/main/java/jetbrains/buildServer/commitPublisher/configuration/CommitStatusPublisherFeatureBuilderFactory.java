package jetbrains.buildServer.commitPublisher.configuration;

import java.util.Optional;
import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import org.jetbrains.annotations.NotNull;

public class CommitStatusPublisherFeatureBuilderFactory {
  private final ExtensionsProvider myExtensions;

  public CommitStatusPublisherFeatureBuilderFactory(@NotNull ExtensionsProvider extensionsProvider) {
    myExtensions = extensionsProvider;
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder createForConnection(@NotNull OAuthConnectionDescriptor connection) {
    String providerType = connection.getProviderType();
    if (providerType == null) {
      throw new IllegalArgumentException("No connection provider type specified for connection: " + connection);
    }

    return createForProviderType(providerType);
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder createForProviderType(@NotNull String providerType) {
    Optional<CommitStatusPublisherFeatureBuilderService> service = myExtensions.getExtensions(CommitStatusPublisherFeatureBuilderService.class).stream()
                                                                               .filter(s -> s.supportsVcsHostingType(providerType))
                                                                               .findFirst();
    if (!service.isPresent()) {
      throw new IllegalArgumentException("Unsupported OAuth connection type: " + providerType);
    }

    String publisherId = service.get().getPublisherId();
    final Optional<CommitStatusPublisherSettings> maybeSettings = myExtensions.getExtensions(CommitStatusPublisherSettings.class).stream()
                                                                              .filter(s -> publisherId.equals(s.getId()))
                                                                              .findFirst();

    if (!maybeSettings.isPresent()) {
      throw new IllegalStateException("publisher settings extension bean not found for publisher " + publisherId);
    }

    return service.get().createFeatureBuilder(maybeSettings.get());
  }
}
