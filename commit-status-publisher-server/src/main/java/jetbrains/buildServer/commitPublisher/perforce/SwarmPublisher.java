package jetbrains.buildServer.commitPublisher.perforce;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.HttpBasedCommitStatusPublisher;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * @author kir
 */
class SwarmPublisher extends HttpBasedCommitStatusPublisher {

  public SwarmPublisher(@NotNull SwarmPublisherSettings swarmPublisherSettings,
                        @NotNull SBuildType buildType,
                        @NotNull String buildFeatureId,
                        @NotNull Map<String, String> params,
                        @NotNull CommitStatusPublisherProblems problems) {
    super(swarmPublisherSettings, buildType, buildFeatureId, params, problems);
  }

  @NotNull
  @Override
  public String getId() {
    return SwarmPublisherSettings.ID;
  }

  @Override
  public String toString() {
    return "perforceSwarm";
  }
}
