package jetbrains.buildServer.commitPublisher;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

public abstract class InformativeCommitStatusFilter<T> implements Predicate<T> {
  private final Set<String> myPossibleBuildUrls;
  private final Set<String> myQueuedUrlsForRemovedBuilds = new HashSet<>();

  public InformativeCommitStatusFilter(Set<String> possibleBuildUrls) {
    myPossibleBuildUrls = possibleBuildUrls;
  }
  
  protected abstract String getUrl(T status);
  protected abstract String getDescription(T status);

  @Override
  public boolean test(T status) {
    String url = getUrl(status);
    if (myPossibleBuildUrls.contains(url)) {  // should not be same build
      return false;
    }
    if (myQueuedUrlsForRemovedBuilds.contains(url)) { // build should not be removed from queue already
      return false;
    }
    String description = getDescription(status);
    if (description == null) {
      return true;
    }
    if (description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)) {  // should not be removed from queue
      String expectedQueuedStatusUrl = buildQueuedUrl(status);
      if (expectedQueuedStatusUrl != null) {
        myQueuedUrlsForRemovedBuilds.add(expectedQueuedStatusUrl);
      }
      return false;
    }
    return true;
  }

  private String buildQueuedUrl(@NotNull T status) {
    String url = getUrl(status);
    if (url == null) return null;
    int endOfBaseUrl = url.indexOf("viewLog.html?");
    if (endOfBaseUrl < 0) {
      return null;
    }
    final String baseUrl = url.substring(0, endOfBaseUrl - 1);
    int buildIdStart = url.indexOf("buildId=");
    if (buildIdStart < 0) {
      return null;
    }
    buildIdStart+="buildId=".length();
    int buildIdEnd = url.indexOf('&', buildIdStart);
    final String buildId = url.substring(buildIdStart, buildIdEnd < 0 ? url.length() : buildIdEnd);
    return baseUrl + "/viewQueued.html?itemId=" +  buildId;
  }
}
