package jetbrains.buildServer.commitPublisher.stash;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class StashBuildNameProvider extends BaseBuildNameProvider {
  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion, Map<String, String> params) {
    String buildName = getCustomBuildNameFromParameters(params);
    if (!StringUtil.isEmptyOrSpaces(buildName)) {
      return buildName;
    }

    SBuild associatedBuild = promotion.getAssociatedBuild();
    String basicBuildName = super.getBuildName(promotion, params);
    if (associatedBuild != null) {
      return basicBuildName + " #" + associatedBuild.getBuildNumber();
    }
    return basicBuildName;
  }

  @NotNull
  @Override
  public String getDefaultBuildName(@NotNull SBuildType buildType) {
    return super.getDefaultBuildName(buildType) + " #<BUILD_NUMBER>";
  }
}
