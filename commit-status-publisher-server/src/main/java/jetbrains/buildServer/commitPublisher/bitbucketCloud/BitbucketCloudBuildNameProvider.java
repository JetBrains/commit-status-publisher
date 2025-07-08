package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

public class BitbucketCloudBuildNameProvider extends BaseBuildNameProvider {
  private static final String BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE = "commitStatusPublisher.bitbucket.buildNumberToName.enabled";

  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion) {
    SBuildType buildType = promotion.getBuildType();
    if (buildType == null) return promotion.getBuildTypeExternalId();

    if (isPublishingQueuedStatusEnabled(buildType)) {
      return super.getBuildName(promotion);
    }

    final boolean includeBuildNumberToName = buildType instanceof BuildTypeEx && ((BuildTypeEx)buildType).getBooleanInternalParameterOrTrue(BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE);
    SBuild build = promotion.getAssociatedBuild();
    StringBuilder sb = new StringBuilder(super.getBuildName(promotion));
    if (includeBuildNumberToName && build != null) {
      sb.append(" #").append(build.getBuildNumber());
    }
    return sb.toString();
  }

  private boolean isPublishingQueuedStatusEnabled(@NotNull SBuildType buildType) {
    String parameterValue = buildType.getParameterValue(BasePublisherSettings.PARAM_PUBLISH_BUILD_QUEUED_STATUS);
    return parameterValue == null || Boolean.parseBoolean(parameterValue);
  }
}
