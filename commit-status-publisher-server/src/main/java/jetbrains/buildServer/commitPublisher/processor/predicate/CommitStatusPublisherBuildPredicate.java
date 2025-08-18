package jetbrains.buildServer.commitPublisher.processor.predicate;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.DependencyConsumer;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class CommitStatusPublisherBuildPredicate implements BuildPredicate {
  @Override
  public boolean test(@NotNull SBuild build) {
    final BuildPromotion buildPromotion = build.getBuildPromotion();
    if (supportsCommitStatusPublisherPlugin(buildPromotion)) {
      return canBePromoted((BuildPromotionEx)buildPromotion);
    }
    return false;
  }

  private boolean supportsCommitStatusPublisherPlugin(@NotNull BuildPromotion buildPromotion) {
    return !buildPromotion.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE).isEmpty();
  }

  private boolean canBePromoted(@NotNull BuildPromotionEx buildPromotion) {
    AtomicReference<Boolean> isCommitStatusPublisherPluginUsedByDependents= new AtomicReference<>(false);
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
