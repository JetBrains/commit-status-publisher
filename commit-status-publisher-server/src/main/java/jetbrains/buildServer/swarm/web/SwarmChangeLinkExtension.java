package jetbrains.buildServer.swarm.web;

import java.util.Map;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.web.openapi.ChangeDetailsExtension;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PositionConstraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swarm.SwarmConstants.PLUGIN_NAME;

/**
 * Show link to Swarm server change near each Perforce commit which has an associated Swarm server
 */
public class SwarmChangeLinkExtension extends ChangeDetailsExtension {
  private final SwarmClientManager mySwarmClients;
  private final ProjectManager projectManager;

  public SwarmChangeLinkExtension(@NotNull PagePlaces pagePlaces, @NotNull SwarmClientManager swarmClients, @NotNull ProjectManager projectManager) {
    super(pagePlaces, PlaceId.CHANGE_DETAILS_BLOCK, PLUGIN_NAME, "swarm/swarmLink.jsp");
    mySwarmClients = swarmClients;
    this.projectManager = projectManager;

    setPosition(PositionConstraint.first());
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!super.isAvailable(request)) {
      return false;
    }

    return null != getSwarmClient(request) && getChangelistId(request) > 0;
  }

  @Nullable
  private SwarmClient getSwarmClient(@NotNull HttpServletRequest request) {
    return mySwarmClients.getSwarmClient(Objects.requireNonNull(getBuildType(request, projectManager)), getVcsModification(request).getVcsRoot());
  }

  @Override
  protected boolean requiresBuildTypeContext() {
    return true;
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    fillCompactMode(model, request);

    SwarmClient swarmClient = getSwarmClient(request);
    assert swarmClient != null;

    model.put("swarmChangeUrl", swarmClient.getSwarmServerUrl() + "/changes/" + getChangelistId(request));
  }

  private int getChangelistId(@NotNull HttpServletRequest request) {
    SVcsModification modification = getVcsModification(request);
    try {
      return Integer.parseInt(Objects.requireNonNull(modification.getDisplayVersion()));
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
