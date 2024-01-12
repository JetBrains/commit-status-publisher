

package jetbrains.buildServer.commitPublisher.stash.data;

import org.jetbrains.annotations.NotNull;

public class JsonStashBuildStatus {
  public final String buildNumber, description, key, parent, name, ref, url;
  public final String state;
  public final Long duration;
  public final StashTestStatistics testResults;

  public JsonStashBuildStatus(@NotNull DeprecatedJsonStashBuildStatuses.Status status) {
    state = status.state;
    description = status.description;
    key = status.key;
    name = status.name;
    url = status.url;
    buildNumber = null;
    parent = null;
    ref = null;
    duration = null;
    testResults = null;
  }

  public static class StashTestStatistics {
    public int failed, skipped, successful;
  }

  public JsonStashBuildStatus(String buildNumber,
                              String description,
                              String key,
                              String parent,
                              String name,
                              String ref,
                              String url,
                              String state,
                              Long duration,
                              StashTestStatistics testResults) {
    this.buildNumber = buildNumber;
    this.description = description;
    this.key = key;
    this.parent = parent;
    this.name = name;
    this.ref = ref;
    this.url = url;
    this.state = state;
    this.duration = duration;
    this.testResults = testResults;
  }
}