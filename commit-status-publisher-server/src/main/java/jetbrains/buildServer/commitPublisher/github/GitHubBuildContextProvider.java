package jetbrains.buildServer.commitPublisher.github;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitHubBuildContextProvider extends BaseBuildNameProvider {

  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion, Map<String, String> params) throws GitHubContextResolveException {
    String context;
    try {
      context = getCustomBuildNameFromParameters(params);
    } catch (IllegalStateException e) {
      throw new GitHubContextResolveException(e.getMessage());
    }

    return StringUtil.isEmptyOrSpaces(context) ? getDefaultBuildName(promotion) : context;
  }

  @NotNull
  private String getDefaultBuildName(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType != null) {
      final SProject project = buildType.getProject();
      String pipelineContext = getPipelineName(project, (BuildPromotionEx)buildPromotion);
      if (pipelineContext != null) {
        return pipelineContext;
      }

      String btName = removeMultiCharUnicodeAndTrim(buildType.getName());
      String prjName = removeMultiCharUnicodeAndTrim(project.getName());
      return String.format("%s (%s)", btName, prjName);
    } else {
      return "<Removed build configuration>";
    }
  }

  /**
   * The default Build Name (context) for Pipelines is calculated differently than for common Build configurations,
   * so if this value is used in the Pipelines, the separate calculation approach should be implemented.
   */
  @NotNull
  @Override
  public String getDefaultBuildName(@NotNull SBuildType buildType) {
    String btName = removeMultiCharUnicodeAndTrim(buildType.getName());
    String prjName = removeMultiCharUnicodeAndTrim(buildType.getProject().getName());
    return String.format("%s (%s)", btName, prjName);
  }

  @Nullable
  private String getPipelineName(@NotNull SProject project, @NotNull BuildPromotionEx promotion) {
    if (promotion.getPipelineView().isHead()) {
      String projectName = removeMultiCharUnicodeAndTrim(project.getName());
      SProject parentProject = project.getParentProject();
      if (parentProject != null) {
        String parentProjectName = removeMultiCharUnicodeAndTrim(parentProject.getName());
        return String.format("%s (%s)", projectName, parentProjectName);
      }

      return removeMultiCharUnicodeAndTrim(projectName);
    }
    if (promotion.getPipelineView().isJob()) {
      String jobName = promotion.getPipelineView().getJobName();
      SProject pipelineProject = project.getParentProject();
      if (jobName != null && pipelineProject != null) {
        return String.format("%s (%s)", removeMultiCharUnicodeAndTrim(jobName), removeMultiCharUnicodeAndTrim(pipelineProject.getName()));
      }
    }

    return null;
  }

  private String removeMultiCharUnicodeAndTrim(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c: s.toCharArray()) {
      if (c >= 0xd800L && c <= 0xdfffL || (c & 0xfff0) == 0xfe00 || c == 0x20e3 || c == 0x200d) {
        continue;
      }
      sb.append(c);
    }
    return sb.toString().trim();
  }
}
