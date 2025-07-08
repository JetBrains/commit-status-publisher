package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseBuildNameProvider implements StatusPublisherBuildNameProvider {
  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion) {
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
    if (promotion.getPipelineInfo().isHead()) {
      return project.getFullName();
    }
    if (promotion.getPipelineInfo().isJob()) {
      SProject pipelineProject = project.getParentProject();
      if (pipelineProject == null) {
        return promotion.getPipelineInfo().getJobName();
      }
      return pipelineProject.getFullName() + BuildTypeDescriptor.FULL_NAME_SEPARATOR + promotion.getPipelineInfo().getJobName();
    }
    return null;
  }
}
