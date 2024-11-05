/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
