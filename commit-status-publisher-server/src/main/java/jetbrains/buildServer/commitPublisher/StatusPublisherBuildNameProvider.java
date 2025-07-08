package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

public interface StatusPublisherBuildNameProvider {
  @NotNull
  String getBuildName(@NotNull BuildPromotion buildPromotion);
}
