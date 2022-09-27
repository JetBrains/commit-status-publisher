package jetbrains.buildServer.swarm.web;

import java.util.Map;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsRootInstance;
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
  private static final String SHELVED_PREFIX = "(shelved changelist @=";

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

  @Override
  protected boolean isSupported(@NotNull SVcsModification modification) {
    // Support non-personal modifications, too
    return true;
  }

  @Nullable
  private SwarmClient getSwarmClient(@NotNull HttpServletRequest request) {
    SBuildType buildType = Objects.requireNonNull(getBuildType(request, projectManager));
    SVcsModification vcsModification = getVcsModification(request);
    if (!vcsModification.isPersonal()) {
      return mySwarmClients.getSwarmClient(buildType, vcsModification.getVcsRoot());
    }

    // Personal VCS change, find first matching SwarmClient
    // Unfortunately, looks like there is no way to find out the exact VCS root this
    // modification belongs to, it is not in the request context
    for (VcsRootInstance vcsRootInstance : buildType.getVcsRootInstances()) {
      SwarmClient client = mySwarmClients.getSwarmClient(buildType, vcsRootInstance);
      if (client != null) {
        return client;
      }
    }

    return null;
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
      if (!modification.isPersonal()) {
        return Integer.parseInt(Objects.requireNonNull(modification.getDisplayVersion()));
      }
      else {
        String description = modification.getDescription();
        int idx = description.indexOf(SHELVED_PREFIX);
        if (idx > 0) {
          return Integer.parseInt(description.substring(idx + SHELVED_PREFIX.length(), description.length() - 1));
        }
      }
    } catch (NumberFormatException ignored) {
      //
    }
    return -1;
  }
}
