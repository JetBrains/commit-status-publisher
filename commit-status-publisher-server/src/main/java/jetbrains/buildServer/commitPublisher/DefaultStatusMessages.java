package jetbrains.buildServer.commitPublisher;

public interface DefaultStatusMessages {
  String BUILD_QUEUED = "TeamCity build was queued";
  String BUILD_REMOVED_FROM_QUEUE = "TeamCity build was removed from queue";
  String BUILD_REMOVED_FROM_QUEUE_AS_CANCELED = "TeamCity build was canceled by a failing dependency";
  String BUILD_STARTED = "TeamCity build started";
  String BUILD_FINISHED = "TeamCity build finished";
  String BUILD_FAILED = "TeamCity build failed";
  String BUILD_MARKED_SUCCESSFULL = "TeamCity build was marked as successful";
}
