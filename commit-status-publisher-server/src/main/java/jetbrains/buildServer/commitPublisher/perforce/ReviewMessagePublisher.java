package jetbrains.buildServer.commitPublisher.perforce;

import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

/**
 * @author kir
 */
interface ReviewMessagePublisher {
  void publishMessage(@NotNull Long reviewId, @NotNull BuildPromotion build, @NotNull String debugBuildInfo) throws PublisherException;
}
