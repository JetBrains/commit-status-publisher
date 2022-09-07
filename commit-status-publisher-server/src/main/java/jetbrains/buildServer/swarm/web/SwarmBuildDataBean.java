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
import org.jetbrains.annotations.Nullable;

public class SwarmBuildDataBean {

  private final ConcurrentMap<String, SwarmServerData> mySwarmServers = new ConcurrentHashMap<>();
  private Date myLastRetrievedTime;
  private Throwable myReportedError;

  public void addData(@NotNull String swarmServerUrl, @NotNull ReviewLoadResponse reviews) {
    updateLastRetrievedTimeFrom(reviews);
    
    if (reviews.getError() != null) {
      setError(reviews.getError(), reviews);
    }

    if (!reviews.getReviews().isEmpty()) {
      mySwarmServers.computeIfAbsent(swarmServerUrl, SwarmServerData::new).addAll(reviews.getReviews());
    }
  }

  private void updateLastRetrievedTimeFrom(@NotNull ReviewLoadResponse reviews) {
    if (myLastRetrievedTime == null || myLastRetrievedTime.after(reviews.getCreated())) {
      myLastRetrievedTime = reviews.getCreated();
    }
  }

  public boolean isReviewsPresent() {
    return !mySwarmServers.isEmpty();
  }

  public boolean isHasData() {
    return myLastRetrievedTime != null;
  }

  public List<SwarmServerData> getReviews() {
    return new ArrayList<>(mySwarmServers.values());
  }

  @NotNull
  public Duration getRetrievedAge() {
    if (myLastRetrievedTime == null) {
      return Duration.ZERO;
    }
    return Duration.between(ZonedDateTime.ofInstant(myLastRetrievedTime.toInstant(), ZoneId.systemDefault()), ZonedDateTime.now());
  }

  public void setError(@NotNull Throwable e, @Nullable ReviewLoadResponse reviews) {
    myReportedError = e;
    if (reviews != null) {
      updateLastRetrievedTimeFrom(reviews);
    }
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
