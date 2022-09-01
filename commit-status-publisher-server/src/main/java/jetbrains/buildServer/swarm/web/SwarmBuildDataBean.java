package jetbrains.buildServer.swarm.web;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class SwarmBuildDataBean {

  private final ConcurrentMap<String, SwarmServerData> mySwarmServers = new ConcurrentHashMap<>();

  public void addData(@NotNull String swarmServerUrl, @NotNull List<Long> reviewIds) {
    mySwarmServers.computeIfAbsent(swarmServerUrl, SwarmServerData::new).addAll(reviewIds);
  }

  public boolean isDataPresent() {
    return !mySwarmServers.isEmpty();
  }

  public List<SwarmServerData> getReviews() {
    return new ArrayList<>(mySwarmServers.values());
  }

  public static class SwarmServerData {
    private final String myUrl;
    private final Set<Long> myReviews = new HashSet<>();

    public SwarmServerData(@NotNull String url) {
      myUrl = StringUtil.removeTailingSlash(url);
    }

    public void addAll(@NotNull List<Long> reviewIds) {
      myReviews.addAll(reviewIds);
    }

    public String getUrl() {
      return myUrl;
    }

    public List<Long> getReviewIds() {
      ArrayList<Long> result = new ArrayList<>(myReviews);
      result.sort(Long::compareTo);
      return result;
    }
  }
}
