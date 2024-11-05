/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import jetbrains.buildServer.swarm.SwarmChangelist;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwarmBuildDataBean {

  private final ConcurrentMap<SwarmChangelist, SwarmServerData> mySwarmServers = new ConcurrentHashMap<>();
  private final Collection<SwarmChangelist> mySwarmClientRevisions;
  private Date myLastRetrievedTime;
  private Throwable myReportedError;

  public SwarmBuildDataBean(@NotNull Collection<SwarmChangelist> swarmClientRevisions) {
    mySwarmClientRevisions = swarmClientRevisions;
  }

  public void addData(@NotNull SwarmChangelist swarmChangelist, @NotNull ReviewLoadResponse reviews) {
    updateLastRetrievedTimeFrom(reviews);
    
    if (reviews.getError() != null) {
      setError(reviews.getError(), reviews);
    }

    if (!reviews.getReviews().isEmpty()) {
      mySwarmServers.computeIfAbsent(swarmChangelist, SwarmServerData::new).addAll(reviews.getReviews());
    }
  }

  public Collection<SwarmChangelist> getSwarmChangelists() {
    return mySwarmClientRevisions;
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
    private final Set<SingleReview> myReviews = new HashSet<>();
    private final SwarmChangelist mySwarmChangelist;

    public SwarmServerData(@NotNull SwarmChangelist swarmChangelist) {
      mySwarmChangelist = swarmChangelist;
    }

    public void addAll(@NotNull List<SingleReview> reviews) {
      myReviews.addAll(reviews);
    }

    public String getUrl() {
      return mySwarmChangelist.getSwarmClient().getSwarmServerUrl();
    }

    public List<Long> getReviewIds() {
      return myReviews.stream().map((r) -> r.getId()).sorted().collect(Collectors.toList());
    }

    public List<SingleReview> getReviews() {
      return myReviews.stream().sorted().collect(Collectors.toList());
    }

    public boolean isShelved() {
      return mySwarmChangelist.isShelved();
    }

    public long getChangelist() {
      return mySwarmChangelist.getChangelist();
    }
  }
}
