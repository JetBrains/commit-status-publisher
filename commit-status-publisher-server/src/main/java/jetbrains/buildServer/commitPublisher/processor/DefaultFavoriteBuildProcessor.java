package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerSupplier;
import jetbrains.buildServer.favoriteBuilds.FavoriteBuildsManager;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.DependencyConsumer;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class DefaultFavoriteBuildProcessor implements FavoriteBuildProcessor{

  private static final PropertyKey USER_PROPERTY = new SimplePropertyKey(Constants.USER_AUTO_FAVORITE_IMPORTANT_BUILDS_PROPERTY);
  private final FavoriteBuildsManager myFavoriteBuildsManager;

  public DefaultFavoriteBuildProcessor(@NotNull FavoriteBuildsManager favoriteBuildsManager) {
    myFavoriteBuildsManager = favoriteBuildsManager;
  }

  private boolean shouldMarkAsFavorite(@NotNull final SUser user) {
    return user.getBooleanProperty(USER_PROPERTY);
  }

  @Override
  public boolean markAsFavorite(@NotNull final SBuild build, @NotNull final BuildOwnerSupplier buildOwnerSupplier) {
    final BuildPromotion buildPromotion = build.getBuildPromotion();
    if (isStillRunning(build) && isSupported(buildPromotion)) {
      return buildOwnerSupplier.supplyFrom(build).stream()
        .filter(this::shouldMarkAsFavorite)
        .peek(candidate -> myFavoriteBuildsManager.tagBuild(buildPromotion, candidate))
        .count() > 0;
    }
    return false;
  }

  private boolean isStillRunning(@NotNull final SBuild build) {
    return !build.isFinished();
  }

  private boolean isSupported(@NotNull final BuildPromotion buildPromotion) {
    if (hasCommitStatusPublisherFeature(buildPromotion) && !isAlreadyTagged(buildPromotion) && (buildPromotion instanceof BuildPromotionEx)) {
      // if commit status publisher build feature is enabled in one of the dependent builds, we have to skip this build.
      return !isCommitStatusPublisherFeatureEnabledInDependentBuilds((BuildPromotionEx) buildPromotion);
    }
    return false;
  }

  private boolean isAlreadyTagged(@NotNull BuildPromotion buildPromotion) {
    return buildPromotion.getTagDatas().stream().anyMatch(tagData -> tagData.getLabel().equals(FavoriteBuildsManager.FAVORITE_BUILD_TAG));
  }

  private boolean hasCommitStatusPublisherFeature(@NotNull final BuildPromotion buildPromotion) {
    return !buildPromotion.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE).isEmpty();
  }

  private boolean isCommitStatusPublisherFeatureEnabledInDependentBuilds(@NotNull final BuildPromotionEx buildPromotion) {
    final AtomicBoolean isCommitStatusPublisherFeatureUsedByDependents = new AtomicBoolean(false);
    buildPromotion.traverseDependedOnMe(dependent -> {
      if(hasCommitStatusPublisherFeature(dependent)) {
        isCommitStatusPublisherFeatureUsedByDependents.set(true);
        return DependencyConsumer.Result.STOP;
      }
      return DependencyConsumer.Result.CONTINUE;
    });
    return isCommitStatusPublisherFeatureUsedByDependents.get();
  }
}
