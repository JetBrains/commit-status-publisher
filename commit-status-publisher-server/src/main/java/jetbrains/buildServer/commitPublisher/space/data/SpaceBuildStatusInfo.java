package jetbrains.buildServer.commitPublisher.space.data;

import java.util.List;

/**
 * ExternalCheckDTO
 */
public class SpaceBuildStatusInfo {
  public final List<String> changes;
  public final String executionStatus;
  public final String description;
  public final Long timestamp;
  public final String taskId;
  public final String taskName;
  public final String url;
  public final String externalServiceName;

  public SpaceBuildStatusInfo(List<String> changes,
                              String executionStatus,
                              String description,
                              Long timestamp,
                              String taskId,
                              String taskName,
                              String url,
                              String externalServiceName) {
    this.changes = changes;
    this.executionStatus = executionStatus;
    this.description = description;
    this.timestamp = timestamp;
    this.taskId = taskId;
    this.taskName = taskName;
    this.url = url;
    this.externalServiceName = externalServiceName;
  }

  public SpaceBuildStatusInfo(String executionStatus, String description, Long timestamp, String taskName, String url) {
    this(null, executionStatus, description, timestamp, null, taskName, url, null);
  }
}
