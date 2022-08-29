package jetbrains.buildServer.swarm;

import java.util.Map;
import jetbrains.buildServer.serverSide.RelativeWebLinks;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;

public class SwarmClientManager {
  
  private final RelativeWebLinks myWebLinks;
  private final SSLTrustStoreProvider myTrustStoreProvider;

  private final int myConnectionTimeout = TeamCityProperties.getInteger("teamcity.internal.swarm.connectionTimeout", 10000);

  public SwarmClientManager(@NotNull RelativeWebLinks webLinks, @NotNull SSLTrustStoreProvider trustStoreProvider) {
    myWebLinks = webLinks;
    myTrustStoreProvider = trustStoreProvider;
  }

  @NotNull
  public SwarmClient getSwarmClient(@NotNull Map<String, String> params) {
    return new SwarmClient(myWebLinks, params, myConnectionTimeout, myTrustStoreProvider.getTrustStore());
  }

  public SwarmClient getSwarmClient(@NotNull SBuildType buildType, @NotNull VcsRootInstance root) {
    // todo
    return null;
  }
}
