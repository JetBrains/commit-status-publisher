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

import org.jetbrains.annotations.NotNull;

public class SingleReview implements Comparable {
  private final long myId;
  private final String myStatus;

  public SingleReview(long id, String status) {
    myId = id;
    myStatus = status;
  }

  public long getId() {
    return myId;
  }

  public boolean isOpen() {
    return "needsReview".equals(myStatus) || "needsRevision".equals(myStatus);
  }

  public String getStatusText() {
    if ("needsReview".equals(myStatus)) {
      return "needs review";
    }
    if ("needsRevision".equals(myStatus)) {
      return "needs revision";
    }
    return myStatus;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return Long.compare(myId, ((SingleReview)o).myId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SingleReview that = (SingleReview)o;
    return myId == that.myId;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(myId);
  }

  @Override
  public String toString() {
    return "Review{" + myId + ": '" + myStatus + '\'' + '}';
  }
}
