package jetbrains.buildServer.swarm;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class may represent either loaded reviews, or an error while loading reviews.
 */
public class ReviewLoadResponse {
  private final Date myCreated = new Date();
  private final List<SingleReview> myReviews;

  private final Exception myError;

  public ReviewLoadResponse(@NotNull List<SingleReview> reviews) {
    myReviews = reviews;
    myError = null;
  }

  public ReviewLoadResponse(@NotNull Exception loadError) {
    myReviews = Collections.emptyList();
    myError = loadError;
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

  @Nullable
  public Exception getError() {
    return myError;
  }
}
