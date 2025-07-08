package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

public class StashBuildNameProvider extends BaseBuildNameProvider {
  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion) {
    SBuild associatedBuild = promotion.getAssociatedBuild();
    String basicBuildName = super.getBuildName(promotion);
    if (associatedBuild != null) {
      return basicBuildName + " #" + associatedBuild.getBuildNumber();
    }
    return basicBuildName;
  }
}
