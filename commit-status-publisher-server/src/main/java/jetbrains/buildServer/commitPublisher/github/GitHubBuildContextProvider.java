package jetbrains.buildServer.commitPublisher.github;

import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitHubBuildContextProvider extends BaseBuildNameProvider {

  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion) throws GitHubContextResolveException {
    String context = getCustomContextFromParameter(promotion);
    return context != null ? context : getDefaultBuildName(promotion);
  }

  @Nullable
  private String getCustomContextFromParameter(@NotNull BuildPromotion buildPromotion) throws GitHubContextResolveException {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      String value = build.getParametersProvider().get(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM);
      if (value == null) return null;

      if (isRemovedFromQueue(build) && ReferencesResolverUtil.mayContainReference(value)) {
        throw new GitHubContextResolveException("Variables in the custom context for removed from queue build cannot be resolved");
      }
      return build.getValueResolver().resolve(value).getResult();
    }

    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType == null) return null;

    String value = buildType.getParameters().get(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM);
    if (value == null) return null;

    if(ReferencesResolverUtil.mayContainReference(value)) {
      throw new GitHubContextResolveException("Variables in the custom context for build cannot be resolved");
    }
    return value;
  }

  private boolean isRemovedFromQueue(@NotNull SBuild build) {
    return build.isFinished() && build.getCanceledInfo() != null;
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
