package jetbrains.buildServer.swarm.web;

import com.intellij.openapi.util.Pair;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.swarm.ReviewLoadResponse;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.web.openapi.BuildInfoFragmentTab;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwarmBuildPageExtension extends BuildInfoFragmentTab {
  private static final String SWARM_EXTENSION = "swarm";

  static final String SWARM_REVIEWS_ENABLED = "teamcity.swarm.reviews.enabled";
  static final String SWARM_BEAN = "swarmBean";
  static final String SWARM_CHANGELISTS = "swarmChangelists";

  private final SwarmClientManager mySwarmClients;

  public SwarmBuildPageExtension(@NotNull SBuildServer server,
                                 @NotNull WebControllerManager manager,
                                 @NotNull final PluginDescriptor desc,
                                 @NotNull SwarmClientManager swarmClients) {
    super(server, manager, SWARM_EXTENSION, desc.getPluginResourcesPath("swarm/swarmBuildPageFragment.jsp"));
    mySwarmClients = swarmClients;
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!TeamCityProperties.getBoolean(SWARM_REVIEWS_ENABLED) || !super.isAvailable(request)) {
      return false;
    }
    SBuild build = Objects.requireNonNull(getBuild(request));
    return !mySwarmClients.getSwarmClientRevisions(build).isEmpty();
  }

  @Override
  public String getDisplayName() {
    // Also see text label at swarmBuildPageFragment.jsp
    return "Swarm Reviews";
  }

  @Override
  protected void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request, @Nullable SBuild build) {
    if (build == null) {
      return;
    }
    SBuildType buildType = build.getBuildType();
    if (buildType == null) {
      return;
    }

    Set<Pair<SwarmClient, String>> swarmClientRevisions = mySwarmClients.getSwarmClientRevisions(build);
    SwarmBuildDataBean bean = createSwarmDataBean(build, buildType, swarmClientRevisions, false);

    model.put(SWARM_BEAN, bean);
    model.put(SWARM_CHANGELISTS, swarmClientRevisions.stream()
                                                     .map((r) -> r.second)
                                                     .distinct()
                                                     .collect(Collectors.joining(",")));
  }

  @NotNull
  private static SwarmBuildDataBean createSwarmDataBean(@NotNull SBuild build, @NotNull SBuildType buildType, Set<Pair<SwarmClient, String>> swarmClientRevisions, boolean forceLoadData) {

    SwarmBuildDataBean bean = new SwarmBuildDataBean();
    final String debugBuildInfo = "build [id=" + build.getBuildId() + "] in " + buildType.getExtendedFullName();

    Loggers.SERVER.debug("Getting Swarm data for " + debugBuildInfo + "; forced: " + forceLoadData);
    for (Pair<SwarmClient, String> swarmClientRevision : swarmClientRevisions) {
      try {
        SwarmClient swarmClient = swarmClientRevision.first;
        ReviewLoadResponse reviews = forceLoadData ?
                                     swarmClient.getReviews(swarmClientRevision.second, debugBuildInfo, true) :
                                     swarmClient.getCachedReviews(swarmClientRevision.second);

        if (reviews != null) {
          if (reviews.getError() != null) {
            bean.setError(reviews.getError());
          }
          else {
            bean.addData(swarmClient.getSwarmServerUrl(), reviews);
          }
        }
      } catch (Throwable e) {
        bean.setError(e);
        Loggers.SERVER.warnAndDebugDetails("Could not load reviews for build " + debugBuildInfo, e);
      }
    }
    return bean;
  }

  public SwarmBuildDataBean forceLoadReviews(@NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null) {
      return null;
    }

    return createSwarmDataBean(build, buildType, mySwarmClients.getSwarmClientRevisions(build), true);
  }
}
