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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.swarm.ReviewLoadResponse;
import jetbrains.buildServer.swarm.SwarmChangelist;
import jetbrains.buildServer.swarm.SwarmClient;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.web.openapi.BuildInfoFragmentTab;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwarmBuildPageExtension extends BuildInfoFragmentTab {
  private static final String SWARM_EXTENSION = "swarm";
  static final String SWARM_BEAN = "swarmBean";

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
    if (!super.isAvailable(request)) {
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

    Set<SwarmChangelist> swarmClientRevisions = mySwarmClients.getSwarmClientRevisions(build);
    SwarmBuildDataBean bean = createSwarmDataBean(build, buildType, swarmClientRevisions, false);

    model.put(SWARM_BEAN, bean);
  }

  @NotNull
  private static SwarmBuildDataBean createSwarmDataBean(@NotNull SBuild build, @NotNull SBuildType buildType, Set<SwarmChangelist> swarmClientRevisions, boolean forceLoadData) {

    SwarmBuildDataBean bean = new SwarmBuildDataBean(swarmClientRevisions);
    final String debugBuildInfo = "build [id=" + build.getBuildId() + "] in " + buildType.getExtendedFullName();

    Loggers.SERVER.debug("Getting Swarm data for " + debugBuildInfo + "; forced: " + forceLoadData);
    for (SwarmChangelist swarmChangelist : swarmClientRevisions) {
      ReviewLoadResponse reviews = null;
      try {
        SwarmClient swarmClient = swarmChangelist.getSwarmClient();
        String changelist = String.valueOf(swarmChangelist.getChangelist());
        reviews = forceLoadData ?
                  swarmClient.getReviews(changelist, debugBuildInfo, true) :
                  swarmClient.getCachedReviews(changelist);

        if (reviews != null) {
          bean.addData(swarmChangelist, reviews);
        }
      } catch (Throwable e) {
        bean.setError(e, reviews);
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

    return IOGuard.allowNetworkCall(() -> {
      return createSwarmDataBean(build, buildType, mySwarmClients.getSwarmClientRevisions(build), true);
    });
  }
}
