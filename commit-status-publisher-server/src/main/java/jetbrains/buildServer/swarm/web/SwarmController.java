package jetbrains.buildServer.swarm.web;

import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.swarm.SwarmConstants;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = SwarmConstants.PLUGIN_NAME + "/swarm")
public class SwarmController {
  private final SwarmBuildPageExtension mySwarmBuildPageExtension;
  private final BuildsManager myBuildManager;

  public SwarmController(@NotNull SwarmBuildPageExtension swarmBuildPageExtension, @NotNull BuildsManager buildManager) {
    mySwarmBuildPageExtension = swarmBuildPageExtension;
    myBuildManager = buildManager;
  }

  @RequestMapping(method = RequestMethod.POST, path = "/loadReviews")
  public ResponseEntity<String> loadReviews(@NotNull String buildId) {
    SBuild build = myBuildManager.findBuildInstanceById(Long.parseLong(buildId));
    if (build != null) {
      SwarmBuildDataBean bean = mySwarmBuildPageExtension.forceLoadReviews(build);
      if (bean != null && bean.getError() != null) {
        return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(bean.getError().getMessage());
      }
    }
    else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Build not found: " + buildId);
    }
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("OK");
  }
}
