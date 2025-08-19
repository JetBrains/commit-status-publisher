package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerSupplier;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

public interface FavoriteBuildProcessor {
  /**
   * Mark the input build as favorite for all the users retrieved by the {@link BuildOwnerSupplier} class.
   * @param build input build instance.
   * @param buildOwnerSupplier input build owner strategy for retrieving all the users from the input build.
   */
  boolean markAsFavorite(@NotNull final SBuild build, @NotNull final BuildOwnerSupplier buildOwnerSupplier);
}
