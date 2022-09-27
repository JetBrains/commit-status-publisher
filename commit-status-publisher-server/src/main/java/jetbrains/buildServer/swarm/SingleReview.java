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
}
