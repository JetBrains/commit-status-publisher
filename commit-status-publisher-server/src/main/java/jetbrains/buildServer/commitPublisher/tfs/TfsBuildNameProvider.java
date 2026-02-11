package jetbrains.buildServer.commitPublisher.tfs;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.commitPublisher.github.GitHubContextResolveException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class TfsBuildNameProvider extends BaseBuildNameProvider {
  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion, Map<String, String> params) throws GitHubContextResolveException {
    String context = getCustomBuildNameFromParameters(params);
    return StringUtil.isEmptyOrSpaces(context) ? super.getBuildName(promotion, params) : context;
  }

}
