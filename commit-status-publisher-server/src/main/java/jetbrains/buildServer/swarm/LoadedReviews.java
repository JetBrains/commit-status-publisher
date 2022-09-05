package jetbrains.buildServer.swarm;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class LoadedReviews {
  private final Date myCreated = new Date();
  private final List<SingleReview> myReviews;

  public LoadedReviews(@NotNull List<SingleReview> reviews) {
    myReviews = reviews;
  }

  public List<Long> getOpenReviewIds() {
    return myReviews.stream()
                    .filter((r) -> r.isOpen())
                    .map((r) -> r.getId())
                    .collect(Collectors.toList());
  }

  public Date getCreated() {
    return myCreated;
  }

  public List<SingleReview> getReviews() {
    return myReviews;
  }
}
