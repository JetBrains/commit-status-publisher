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

  @NotNull
  public List<Long> getAllReviewIds() {
    return myReviews.stream()
                    .map(SingleReview::getId)
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
