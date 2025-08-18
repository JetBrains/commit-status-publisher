package jetbrains.buildServer.commitPublisher.processor.predicate;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.favoriteBuilds.FavoriteBuildsManager;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.DependencyConsumer;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class CommitStatusPublisherBuildPredicate implements BuildPredicate {

  @Override
  public boolean test(@NotNull SBuild build) {
    final BuildPromotion buildPromotion = build.getBuildPromotion();
    if (supportsCommitStatusPublisherPlugin(buildPromotion) && !isAlreadyTagged(buildPromotion)) {
      return canBePromoted((BuildPromotionEx)buildPromotion);
    }
    return false;
  }

  private boolean isAlreadyTagged(@NotNull BuildPromotion buildPromotion) {
    return buildPromotion.getTagDatas().stream().anyMatch(tagData -> tagData.getLabel().equals(FavoriteBuildsManager.FAVORITE_BUILD_TAG));
  }

  private boolean supportsCommitStatusPublisherPlugin(@NotNull final BuildPromotion buildPromotion) {
    return !buildPromotion.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE).isEmpty();
  }

  /**
   * Determines if the Commit Status Publisher Plugin is disabled for all dependent builds.
   * @param buildPromotion input build promotion instance, starting point for dependency traversal.
   * @return true if the Commit Status Publisher Plugin is NOT enabled on any of the dependent builds.
   */
  private boolean canBePromoted(@NotNull final BuildPromotionEx buildPromotion) {
    final AtomicBoolean isCommitStatusPublisherPluginUsedByDependents = new AtomicBoolean(false);
    buildPromotion.traverseDependedOnMe(dependent -> {
      if(supportsCommitStatusPublisherPlugin(dependent)) {
        isCommitStatusPublisherPluginUsedByDependents.set(true);
        return DependencyConsumer.Result.STOP;
      }
      return DependencyConsumer.Result.CONTINUE;
    });
    return !isCommitStatusPublisherPluginUsedByDependents.get();
  }
}
