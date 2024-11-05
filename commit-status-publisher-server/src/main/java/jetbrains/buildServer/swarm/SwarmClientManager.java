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

package jetbrains.buildServer.swarm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import jetbrains.buildServer.util.Hash;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.commitPublisher.Constants.PUBLISHER_ID_PARAM;
import static jetbrains.buildServer.commitPublisher.Constants.VCS_ROOT_ID_PARAM;

public class SwarmClientManager {

  public static final String PERFORCE_VCS_NAME = "perforce";

  private final RelativeWebLinks myWebLinks;
  private final SSLTrustStoreProvider myTrustStoreProvider;

  private final int myConnectionTimeout = TeamCityProperties.getInteger("teamcity.internal.swarm.connectionTimeout", 10000);
  private final Cache<Long, SwarmClient> mySwarmClientCache = Caffeine.newBuilder()
                                                                      .executor(Runnable::run)
                                                                      .maximumSize(1000)
                                                                      .build();


  public SwarmClientManager(@NotNull RelativeWebLinks webLinks, @NotNull SSLTrustStoreProvider trustStoreProvider, @NotNull ResetCacheRegister cacheReset) {
    myWebLinks = webLinks;
    myTrustStoreProvider = trustStoreProvider;
    cacheReset.registerHandler(new SwarmResetCacheHandler());
  }

  @NotNull
  public final SwarmClient getSwarmClient(@NotNull Map<String, String> params) {
    long key = Hash.calc(VcsUtil.propertiesToString(params));
    SwarmClient swarmClient = mySwarmClientCache.get(key, (k) -> doCreateSwarmClient(params));
    return Objects.requireNonNull(swarmClient);
  }

  @NotNull
  @VisibleForTesting
  protected SwarmClient doCreateSwarmClient(@NotNull Map<String, String> params) {
    return new SwarmClient(myWebLinks, params, myConnectionTimeout, myTrustStoreProvider.getTrustStore());
  }

  public final SwarmClient getSwarmClient(@NotNull SBuildType buildType, @NotNull VcsRootInstance root) {
    if (!PERFORCE_VCS_NAME.equals(root.getVcsName())) {
      return null;
    }

    Set<Map<String, String>> ourFeatures = new HashSet<Map<String, String>>();
    for (SBuildFeatureDescriptor buildFeature : buildType.getResolvedSettings().getBuildFeatures()) {
      Map<String, String> parameters = buildFeature.getParameters();

      if (CommitStatusPublisherFeature.TYPE.equals(buildFeature.getType()) &&
          SwarmPublisherSettings.ID.equals(parameters.get(PUBLISHER_ID_PARAM)) &&
          buildType.isEnabled(buildFeature.getId())) {

        ourFeatures.add(parameters);
      }
    }

    for (Map<String, String> parameters : ourFeatures) {
      String vcsRootId = parameters.get(VCS_ROOT_ID_PARAM);

      // Either strict match of the VCS Root ID according to build feature settings
      // or there is only one build feature configured - match case "any VCS Root"
      if (root.getExternalId().equals(vcsRootId) || (vcsRootId == null && ourFeatures.size() == 1)) {
        return getSwarmClient(parameters);
      }
    }

    return null;
  }

  /**
   * Returns a collection of SwarmClient with the associated changelistId related to the given build.
   * There could be several such SwarmClients as there could be several Swarm features for the configuration
   *
   * @param build
   * @return see above
   */
  public Set<SwarmChangelist> getSwarmClientRevisions(@NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null) {
      return Collections.emptySet();
    }

    Set<SwarmChangelist> swarmClientRevisions = new HashSet<>();

    for (BuildRevision revision : build.getBuildPromotion().getRevisions()) {
      SwarmClient swarmClient = getSwarmClient(buildType, revision.getRoot());
      if (swarmClient != null) {
        if (build.isPersonal()) {
          String ver = build.getBuildOwnParameters().get("vcsRoot." + revision.getRoot().getExternalId() + ".shelvedChangelist");
          if (StringUtil.isNotEmpty(ver)) {
            swarmClientRevisions.add(new SwarmChangelist(swarmClient, Long.parseLong(ver), true));
          }
        }
        else {
          swarmClientRevisions.add(new SwarmChangelist(swarmClient, Long.parseLong(revision.getRevisionDisplayName()), false));
        }
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
  private class SwarmResetCacheHandler implements ResetCacheHandler {
    private static final String CACHE_NAME = "Perforce Helix Swarm cache";
    @NotNull
    @Override
    public List<String> listCaches() {
      return Arrays.asList(CACHE_NAME);
    }

    @Override
    public boolean isEmpty(@NotNull String name) {
      return mySwarmClientCache.estimatedSize() > 0;
    }

    @Override
    public void resetCache(@NotNull String name) {
      mySwarmClientCache.invalidateAll();
    }
  }
}
