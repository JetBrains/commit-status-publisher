package jetbrains.buildServer.swarm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.serverSide.RelativeWebLinks;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import jetbrains.buildServer.util.Hash;
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
                                                                      .expireAfterWrite(10, TimeUnit.MINUTES)
                                                                      .build();


  public SwarmClientManager(@NotNull RelativeWebLinks webLinks, @NotNull SSLTrustStoreProvider trustStoreProvider) {
    myWebLinks = webLinks;
    myTrustStoreProvider = trustStoreProvider;
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
}