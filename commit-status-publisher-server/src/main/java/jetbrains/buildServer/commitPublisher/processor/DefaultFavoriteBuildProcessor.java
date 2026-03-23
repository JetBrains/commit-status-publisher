package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerSupplier;
import jetbrains.buildServer.favoriteBuilds.FavoriteBuildsManager;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class DefaultFavoriteBuildProcessor implements FavoriteBuildProcessor{
  private static final List<String> TAGS_TO_ADD = Arrays.asList(FavoriteBuildsManager.FAVORITE_BUILD_TAG, Constants.PULL_REQUEST_TAG_LABEL);

  public DefaultFavoriteBuildProcessor() {}

  private boolean shouldMarkAsFavorite(@NotNull final SUser user) {
    return user.getBooleanProperty(Constants.USER_AUTO_FAVORITE_PROPERTY);
  }

  @Override
  public boolean markAsFavorite(@NotNull final BuildPromotion buildPromotion, @NotNull final BuildOwnerSupplier buildOwnerSupplier) {
    if (isAutoFavoriteEnabled() && isSupported(buildPromotion)) {
      return addTagsToBuild(buildPromotion,
        buildOwnerSupplier.supplyFrom(((BuildPromotionEx)buildPromotion).getRealOrDummyBuild()).stream()
        .filter(this::shouldMarkAsFavorite)
        .collect(Collectors.toSet()));
    }
    return false;
  }

  private boolean addTagsToBuild(@NotNull final BuildPromotion buildPromotion, @NotNull final Set<SUser> users) {
    if (users.isEmpty()) {
      return false;
    }

    for (final SUser user : users) {
      buildPromotion.setPrivateTags(TAGS_TO_ADD, user);
    }
    return true;
  }

  private boolean isSupported(@NotNull final BuildPromotion buildPromotion) {
    if (hasCommitStatusPublisherFeature(buildPromotion) && !isAlreadyTagged(buildPromotion) && (buildPromotion instanceof BuildPromotionEx)) {
      // if a commit status publisher build feature is enabled in one of the dependent builds, we have to skip this build.
      return !isCommitStatusPublisherFeatureEnabledInDependentBuilds((BuildPromotionEx) buildPromotion);
    }
    return false;
  }

  private boolean isAutoFavoriteEnabled() {
    return TeamCityProperties.getBoolean(Constants.AUTO_FAVORITE_IMPORTANT_BUILDS_ENABLED);
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
      if (hasCommitStatusPublisherFeature(dependent)) {
        isCommitStatusPublisherFeatureUsedByDependents.set(true);
        return DependencyConsumer.Result.STOP;
      }
      return DependencyConsumer.Result.CONTINUE;
    });
    return isCommitStatusPublisherFeatureUsedByDependents.get();
  }
}
