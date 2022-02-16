package jetbrains.buildServer.commitPublisher;

public interface DefaultStatusMessages {
  public final String BUILD_QUEUED = "TeamCity build queued";
  public final String BUILD_REMOVED_FROM_QUEUE = "TeamCity build removed from queue";
  public final String BUILD_STARTED = "TeamCity build started";
  public final String BUILD_FINISHED = "TeamCity build finished";
  public final String BUILD_FAILED = "TeamCity build failed";
  public final String BUILD_MARKED_SUCCESSFULL = "TeamCity build marked as successful";
}
