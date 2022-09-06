package jetbrains.buildServer.swarm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.util.Pair;
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

  private final RelativeWebLinks myWebLinks;
  private final SSLTrustStoreProvider myTrustStoreProvider;

  private final int myConnectionTimeout = TeamCityProperties.getInteger("teamcity.internal.swarm.connectionTimeout", 10000);
  private final Cache<Long, SwarmClient> mySwarmClientCache = Caffeine.newBuilder()
                                                                      .maximumSize(1000)
                                                                      .build();


  public SwarmClientManager(@NotNull RelativeWebLinks webLinks, @NotNull SSLTrustStoreProvider trustStoreProvider, @NotNull ResetCacheRegister cacheReset) {
    myWebLinks = webLinks;
    myTrustStoreProvider = trustStoreProvider;
    cacheReset.registerHandler(new SwarmResetCacheHandler());
  }

  @NotNull
  public SwarmClient getSwarmClient(@NotNull Map<String, String> params) {
    long key = Hash.calc(VcsUtil.propertiesToString(params));
    SwarmClient swarmClient = mySwarmClientCache.get(key, (k) -> new SwarmClient(myWebLinks, params, myConnectionTimeout, myTrustStoreProvider.getTrustStore()));
    return Objects.requireNonNull(swarmClient);
  }

  public SwarmClient getSwarmClient(@NotNull SBuildType buildType, @NotNull VcsRootInstance root) {

    for (SBuildFeatureDescriptor buildFeature : buildType.getResolvedSettings().getBuildFeatures()) {
      Map<String, String> parameters = buildFeature.getParameters();

      if (CommitStatusPublisherFeature.TYPE.equals(buildFeature.getType()) &&
          SwarmPublisherSettings.ID.equals(parameters.get(PUBLISHER_ID_PARAM)) &&
          buildType.isEnabled(buildFeature.getId())) {

        String vcsRootId = parameters.get(VCS_ROOT_ID_PARAM);
        if (vcsRootId == null || vcsRootId.equals(root.getExternalId())) {
          return getSwarmClient(parameters);
        }
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
  public Set<Pair<SwarmClient, String>> getSwarmClientRevisions(@NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType == null) {
      return Collections.emptySet();
    }

    Set<Pair<SwarmClient, String>> swarmClientRevisions = new HashSet<>();

    for (BuildRevision revision : build.getBuildPromotion().getRevisions()) {
      SwarmClient swarmClient = getSwarmClient(buildType, revision.getRoot());
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
