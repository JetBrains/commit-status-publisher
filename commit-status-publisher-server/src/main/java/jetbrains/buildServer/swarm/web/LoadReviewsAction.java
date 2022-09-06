package jetbrains.buildServer.swarm.web;

import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseAjaxActionController;
import jetbrains.buildServer.controllers.BuildDataExtensionUtil;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.web.openapi.ControllerAction;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /ajax.html?buildId=333&refreshSwarmReviews=true
 */
public class LoadReviewsAction implements ControllerAction {
  public static final String REFRESH_SWARM_REVIEW = "refreshSwarmReviews";
  private final BuildsManager myBuildsManager;
  private final SwarmBuildPageExtension mySwarmBuildPageExtension;

  public LoadReviewsAction(@NotNull BaseAjaxActionController controller, @NotNull BuildsManager buildsManager, @NotNull SwarmBuildPageExtension swarmBuildPageExtension) {
    myBuildsManager = buildsManager;
    mySwarmBuildPageExtension = swarmBuildPageExtension;
    controller.registerAction(this);
  }

  @Override
  public boolean canProcess(@NotNull HttpServletRequest request) {
    return request.getParameter(REFRESH_SWARM_REVIEW) != null && getBuild(request) != null;
  }

  @Nullable
  private SBuild getBuild(@NotNull HttpServletRequest request) {
    return BuildDataExtensionUtil.retrieveBuild(request, myBuildsManager);
  }

  @Override
  public void process(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @Nullable Element ajaxResponse) {
    SBuild build = getBuild(request);

    mySwarmBuildPageExtension.forceLoadReviews(Objects.requireNonNull(build));
  }
}
