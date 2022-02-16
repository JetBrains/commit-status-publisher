package jetbrains.buildServer.commitPublisher.space.data;

/**
 * ExternalCheckDTO
 */
public class SpaceBuildStatusInfo {
  public final String executionStatus;
  public final String description;
  public final Long timestamp;
  public final String taskName;
  public final String url;

  public SpaceBuildStatusInfo(String executionStatus, String description, Long timestamp, String taskName, String url) {
    this.executionStatus = executionStatus;
    this.description = description;
    this.timestamp = timestamp;
    this.taskName = taskName;
    this.url = url;
  }
}
