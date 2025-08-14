package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.processor.suppplier.BuildOwnerStrategy;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

public interface FavoriteBuildProcessor {
  boolean shouldMarkAsFavorite(@NotNull SUser user);
  void markAsFavorite(@NotNull SBuild build, @NotNull BuildOwnerStrategy buildOwnerStrategy);
}
