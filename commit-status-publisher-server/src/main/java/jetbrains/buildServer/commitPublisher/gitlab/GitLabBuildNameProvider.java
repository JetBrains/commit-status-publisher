package jetbrains.buildServer.commitPublisher.gitlab;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.BaseBuildNameProvider;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class GitLabBuildNameProvider extends BaseBuildNameProvider {
  @NotNull
  @Override
  public String getBuildName(@NotNull BuildPromotion promotion, Map<String, String> params){
    String context = getCustomBuildNameFromParameters(params);
    return StringUtil.isEmptyOrSpaces(context) ? super.getBuildName(promotion, params) : context;
  }
}
