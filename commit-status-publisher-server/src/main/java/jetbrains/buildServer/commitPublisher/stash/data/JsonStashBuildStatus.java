package jetbrains.buildServer.commitPublisher.stash.data;

public class JsonStashBuildStatus {
  public String buildNumber, description, key, name, ref, state, url;
  public long duration;
  public StashTestStatistics testResults;
  public static class StashTestStatistics {
    public int failed, skipped, successful;
  }
}
