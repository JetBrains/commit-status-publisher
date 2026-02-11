package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BitbucketCloudBuildNameProvider extends BaseBuildNameProvider {
  private static final String BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE = "commitStatusPublisher.bitbucket.buildNumberToName.enabled";

  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion, Map<String, String> params) {
    String buildName = getCustomBuildNameFromParameters(params);
    if (!StringUtil.isEmptyOrSpaces(buildName)) {
      return buildName;
    }

    SBuildType buildType = promotion.getBuildType();
    if (buildType == null) return promotion.getBuildTypeExternalId();

    if (isPublishingQueuedStatusEnabled(buildType)) {
      return super.getBuildName(promotion, params);
    }

    final boolean includeBuildNumberToName = buildType instanceof BuildTypeEx && ((BuildTypeEx)buildType).getBooleanInternalParameterOrTrue(BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE);
    SBuild build = promotion.getAssociatedBuild();
    StringBuilder sb = new StringBuilder(super.getBuildName(promotion, params));
    if (includeBuildNumberToName && build != null) {
      sb.append(" #").append(build.getBuildNumber());
    }
    return sb.toString();
  }

  @NotNull
  @Override
  public String getDefaultBuildName(@NotNull SBuildType buildType) {
    String defaultBuildName = super.getDefaultBuildName(buildType);

    boolean includeBuildNumberToName = buildType instanceof BuildTypeEx && ((BuildTypeEx)buildType).getBooleanInternalParameterOrTrue(BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE);
    if (isPublishingQueuedStatusEnabled(buildType) || !includeBuildNumberToName) {
      return defaultBuildName;
    }

    return defaultBuildName + "#<BUILD_NUMBER>";
  }

  private boolean isPublishingQueuedStatusEnabled(@NotNull SBuildType buildType) {
    String parameterValue = buildType.getParameterValue(BasePublisherSettings.PARAM_PUBLISH_BUILD_QUEUED_STATUS);
    return parameterValue == null || Boolean.parseBoolean(parameterValue);
  }
}
