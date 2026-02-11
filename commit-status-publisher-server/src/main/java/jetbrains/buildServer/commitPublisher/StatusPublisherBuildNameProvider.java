package jetbrains.buildServer.commitPublisher;

import java.util.Map;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

public interface StatusPublisherBuildNameProvider {
  @NotNull
  String getBuildName(@NotNull BuildPromotion buildPromotion, Map<String, String> params);

  @NotNull
  String getDefaultBuildName(@NotNull SBuildType buildType);
}
