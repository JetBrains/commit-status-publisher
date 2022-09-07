package jetbrains.buildServer.swarm.web;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import jetbrains.buildServer.swarm.ReviewLoadResponse;
import jetbrains.buildServer.swarm.SingleReview;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class SwarmBuildDataBean {

  private final ConcurrentMap<String, SwarmServerData> mySwarmServers = new ConcurrentHashMap<>();
  private Date myLastRetrievedTime = new Date();
  private Throwable myReportedError;

  public void addData(@NotNull String swarmServerUrl, @NotNull ReviewLoadResponse reviews) {
    if (myLastRetrievedTime.after(reviews.getCreated())) {
      myLastRetrievedTime = reviews.getCreated();
    }

    if (!reviews.getReviews().isEmpty()) {
      mySwarmServers.computeIfAbsent(swarmServerUrl, SwarmServerData::new).addAll(reviews.getReviews());
    }
  }

  public boolean isDataPresent() {
    return !mySwarmServers.isEmpty();
  }

  public List<SwarmServerData> getReviews() {
    return new ArrayList<>(mySwarmServers.values());
  }

  @NotNull
  public Duration getRetrievedAge() {
    return Duration.between(ZonedDateTime.ofInstant(myLastRetrievedTime.toInstant(), ZoneId.systemDefault()), ZonedDateTime.now());
  }

  public void setError(Throwable e) {
    myReportedError = e;
  }

  public Throwable getError() {
    return myReportedError;
  }

  public static class SwarmServerData {
    private final String myUrl;
    private final Set<SingleReview> myReviews = new HashSet<>();

    public SwarmServerData(@NotNull String url) {
      myUrl = StringUtil.removeTailingSlash(url);
    }

    public void addAll(@NotNull List<SingleReview> reviews) {
      myReviews.addAll(reviews);
    }

    public String getUrl() {
      return myUrl;
    }

    public List<Long> getReviewIds() {
      return myReviews.stream().map((r) -> r.getId()).sorted().collect(Collectors.toList());
    }

    public List<SingleReview> getReviews() {
      return myReviews.stream().sorted().collect(Collectors.toList());
    }
  }
}
