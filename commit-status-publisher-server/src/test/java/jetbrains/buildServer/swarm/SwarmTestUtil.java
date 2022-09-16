package jetbrains.buildServer.swarm;

import com.google.common.collect.ImmutableMap;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings.PARAM_URL;

public class SwarmTestUtil {
  private SwarmTestUtil() {
  }

  public static SBuildFeatureDescriptor addSwarmFeature(@NotNull BuildTypeEx buildType, @NotNull String url) {
    return buildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, ImmutableMap.of(
      Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID,
      PARAM_URL, url
    ));
  }
}
