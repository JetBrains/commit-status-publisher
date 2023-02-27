package jetbrains.buildServer.commitPublisher.space.data;

/**
 * ExternalCheckDTO
 */
public class SpaceBuildStatusInfo {
  public final String executionStatus;
  public final String description;
  public final Long timestamp;
  public final String taskId;
  public final String taskName;
  public final String url;
  public final String externalServiceName;
  public final String taskBuildId;

  public SpaceBuildStatusInfo(String executionStatus,
                              String description,
                              Long timestamp,
                              String taskName,
                              String url,
                              String taskId,
                              String externalServiceName,
                              String taskBuildId) {
    this.executionStatus = executionStatus;
    this.description = description;
    this.timestamp = timestamp;
    this.taskName = taskName;
    this.url = url;
    this.taskId = taskId;
    this.externalServiceName = externalServiceName;
    this.taskBuildId = taskBuildId;
  }
}
