package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.processor.predicate.BuildPredicate;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerStrategy;
import jetbrains.buildServer.favoriteBuilds.FavoriteBuildsManager;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import org.jetbrains.annotations.NotNull;

public class DefaultFavoriteBuildProcessor implements FavoriteBuildProcessor{

  private static final PropertyKey USER_PROPERTY = new SimplePropertyKey(Constants.USER_AUTOMATICALLY_MARK_IMPORTANT_BUILDS_AS_FAVORITE_INTERNAL_PROPERTY);
  private final FavoriteBuildsManager myFavoriteBuildsManager;

  public DefaultFavoriteBuildProcessor(@NotNull FavoriteBuildsManager favoriteBuildsManager) {
    myFavoriteBuildsManager = favoriteBuildsManager;
  }

  @Override
  public boolean shouldMarkAsFavorite(@NotNull SUser user) {
    return user.getBooleanProperty(USER_PROPERTY);
  }

  @Override
  public void markAsFavorite(@NotNull SBuild build, @NotNull BuildOwnerStrategy buildOwnerStrategy) {
    final BuildPromotion buildPromotion = build.getBuildPromotion();
    buildOwnerStrategy.apply(build)
      .stream()
      .filter(this::shouldMarkAsFavorite)
      .forEach(candidate -> myFavoriteBuildsManager.tagBuild(buildPromotion, candidate));
  }

  @Override
  public boolean isSupported(@NotNull SBuild build, @NotNull BuildPredicate buildPredicate) {
    return buildPredicate.test(build);
  }
}
