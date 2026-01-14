package jetbrains.buildServer.commitPublisher.configuration;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.serverSide.ServerExtension;
import org.jetbrains.annotations.NotNull;

public interface CommitStatusPublisherFeatureBuilderService extends ServerExtension {
  /**
   * @param vcsHostingType The VCS hosting type, the same value is returned by ConnectionProvider::getType
   * @return true if VCS hosting type is supported
   */
  boolean supportsVcsHostingType(@NotNull String vcsHostingType);

  /**
   * @return The publisher id, the same value is returned by HttpBasedCommitStatusPublisher::getId
   */
  @NotNull
  String getPublisherId();

  /**
   * Creates commit status publisher feature builder with specified settings
   * @return commit status publisher feature builder instance
   */
  @NotNull
  CommitStatusPublisherFeatureBuilder createFeatureBuilder(@NotNull CommitStatusPublisherSettings settings);
}
