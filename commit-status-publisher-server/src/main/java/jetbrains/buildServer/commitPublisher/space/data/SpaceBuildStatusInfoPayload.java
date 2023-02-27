package jetbrains.buildServer.commitPublisher.space.data;

import java.util.Collection;

public class SpaceBuildStatusInfoPayload extends SpaceBuildStatusInfo {
  public final Collection<String> changes;
  public SpaceBuildStatusInfoPayload(Collection<String> changes,
                                     String executionStatus,
                                     String description,
                                     Long timestamp,
                                     String taskName,
                                     String url,
                                     String taskId,
                                     String externalServiceName,
                                     String taskBuildId) {
    super(executionStatus, description, timestamp, taskName, url, taskId, externalServiceName, taskBuildId);
    this.changes = changes;
  }
}
