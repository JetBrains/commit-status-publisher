package jetbrains.buildServer.swarm.web;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.web.openapi.BuildInfoFragmentTab;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwarmBuildPageExtension extends BuildInfoFragmentTab {
  private static final String SWARM_EXTENSION = "swarm";

  public SwarmBuildPageExtension(@NotNull SBuildServer server,
                                 @NotNull WebControllerManager manager,
                                 @NotNull final PluginDescriptor desc) {
    super(server, manager, SWARM_EXTENSION, desc.getPluginResourcesPath("swarm/swarmBuildPageFragment.jsp"));
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return TeamCityProperties.getBoolean("teamcity.swarm.reviews.enabled") && super.isAvailable(request);
  }                  

  @Override
  public String getDisplayName() {
    return "Swarm Reviews";
  }

  @Override
  protected void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request, @Nullable SBuild build) {
  }
}
