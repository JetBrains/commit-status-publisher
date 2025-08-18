package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.processor.predicate.BuildPredicate;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerStrategy;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

public interface FavoriteBuildProcessor {
  boolean shouldMarkAsFavorite(@NotNull final SUser user);
  void markAsFavorite(@NotNull final SBuild build, @NotNull final BuildOwnerStrategy buildOwnerStrategy);
  boolean isSupported(@NotNull final SBuild build, @NotNull final BuildPredicate buildPredicate);
}
