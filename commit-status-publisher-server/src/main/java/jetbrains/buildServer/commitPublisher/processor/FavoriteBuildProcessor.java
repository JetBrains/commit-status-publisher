package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.processor.predicate.BuildPredicate;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerStrategy;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

public interface FavoriteBuildProcessor {
  /**
   * Check if a build should be marked as favorite by using the input user.
   * @param user user instance.
   * @return true if the build should be marked as favorite otherwise false.
   */
  boolean shouldMarkAsFavorite(@NotNull final SUser user);

  /**
   * Mark the input build as favorite for all the users retrieved by the {@link BuildOwnerStrategy} class.
   * @param build input build instance.
   * @param buildOwnerStrategy input build owner strategy for retrieving all the users from the input build.
   */
  void markAsFavorite(@NotNull final SBuild build, @NotNull final BuildOwnerStrategy buildOwnerStrategy);

  /**
   * Check if a build is supported by using a {@link BuildPredicate} class.
   * @param build input build instance.
   * @param buildPredicate input build predicate for testing if a build can be processed.
   * @return true if the build can be processed for the marking process, otherwise false.
   */
  boolean isSupported(@NotNull final SBuild build, @NotNull final BuildPredicate buildPredicate);
}
