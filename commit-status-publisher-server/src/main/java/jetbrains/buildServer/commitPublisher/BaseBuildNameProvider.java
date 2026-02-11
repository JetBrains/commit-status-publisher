package jetbrains.buildServer.commitPublisher;

import java.util.Map;
import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.Constants.BUILD_CUSTOM_NAME;
import static jetbrains.buildServer.commitPublisher.Constants.BUILD_NAME_CUSTOMIZATION_TOGGLE_ENABLE;

public abstract class BaseBuildNameProvider implements StatusPublisherBuildNameProvider {
  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion, Map<String, String> params) {
    SBuildType buildType = promotion.getBuildType();
    if (buildType != null) {
      final SProject project = buildType.getProject();
      String pipelineName = getPipelineName(project, (BuildPromotionEx)promotion);
      if (pipelineName != null) {
        return pipelineName;
      }

      return buildType.getFullName();
    }
    return promotion.getBuildTypeExternalId();
  }

  @Nullable
  private String getPipelineName(@NotNull SProject project, @NotNull BuildPromotionEx promotion) {
    if (promotion.getPipelineView().isHead()) {
      return project.getFullName();
    }
    if (promotion.getPipelineView().isJob()) {
      SProject pipelineProject = project.getParentProject();
      if (pipelineProject == null) {
        return promotion.getPipelineView().getJobName();
      }
      return pipelineProject.getFullName() + BuildTypeDescriptor.FULL_NAME_SEPARATOR + promotion.getPipelineView().getJobName();
    }
    return null;
  }

  @NotNull
  @Override
  public String getDefaultBuildName(@NotNull SBuildType buildType) {
    return buildType.getFullName();
  }

  @Nullable
  protected String getCustomBuildNameFromParameters(@NotNull Map<String, String> params) {
    if (!TeamCityProperties.getBoolean(BUILD_NAME_CUSTOMIZATION_TOGGLE_ENABLE)) {
      return null;
    }

    String value = params.get(BUILD_CUSTOM_NAME);

    if (value == null) {
      return null;
    }

    if(ReferencesResolverUtil.mayContainReference(value)) {
      throw new IllegalStateException("Variables in the custom context for build cannot be resolved");
    }

    return value;
  }
}
