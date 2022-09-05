package jetbrains.buildServer.swarm.web;

import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.swarm.LoadedReviews;
import jetbrains.buildServer.util.StringUtil;
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
    SBuildType buildType = build.getBuildType();
    if (buildType == null) {
      return false;
    }

    return !getSwarmClientRevisions(build, buildType).isEmpty();
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

    Set<Pair<SwarmClient, String>> swarmClientRevisions = getSwarmClientRevisions(build, buildType);

    SwarmBuildDataBean bean = new SwarmBuildDataBean();
    final String debugBuildInfo = "build [id=" + build.getBuildId() + "] in " + buildType.getExtendedFullName();
    for (Pair<SwarmClient, String> swarmClientRevision : swarmClientRevisions) {
      try {
        SwarmClient swarmClient = swarmClientRevision.first;
        LoadedReviews reviews = swarmClient.getReviews(swarmClientRevision.second, debugBuildInfo);
        if (!reviews.getReviews().isEmpty()) {
          bean.addData(swarmClient.getSwarmServerUrl(), reviews);
        }
      } catch (PublisherException e) {
        Loggers.SERVER.warnAndDebugDetails("Could not load reviews for build " + debugBuildInfo, e);
      }
    }

    model.put(SWARM_BEAN, bean);
    model.put(SWARM_CHANGELISTS, swarmClientRevisions.stream()
                                                     .map((r) -> r.second)
                                                     .distinct()
                                                     .collect(Collectors.joining(",")));
  }

  @NotNull
  /**
   * There could be multiple VCS Roots and associated Swarm servers per build
   */
  private Set<Pair<SwarmClient, String>> getSwarmClientRevisions(@NotNull SBuild build, @NotNull SBuildType buildType) {
    Set<Pair<SwarmClient, String>> swarmClientRevisions = new HashSet<>();

    for (BuildRevision revision : build.getBuildPromotion().getRevisions()) {
      SwarmClient swarmClient = mySwarmClients.getSwarmClient(buildType, revision.getRoot());
      if (swarmClient != null) {
        swarmClientRevisions.add(new Pair<>(swarmClient, getChangelist(build, revision)));
      }
    }
    return swarmClientRevisions;
  }

  @NotNull
  private String getChangelist(@NotNull SBuild build, @NotNull BuildRevision revision) {
    if (build.isPersonal()) {
      String ver = build.getBuildOwnParameters().get("vcsRoot." + revision.getRoot().getExternalId() + ".shelvedChangelist");
      if (StringUtil.isNotEmpty(ver)) {
        return ver;
      }
    }
    return revision.getRevisionDisplayName();
  }
}