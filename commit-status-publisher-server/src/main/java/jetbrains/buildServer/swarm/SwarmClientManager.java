package jetbrains.buildServer.swarm;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.commitPublisher.Constants.PUBLISHER_ID_PARAM;
import static jetbrains.buildServer.commitPublisher.Constants.VCS_ROOT_ID_PARAM;

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
