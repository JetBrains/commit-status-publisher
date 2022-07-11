package jetbrains.buildServer.commitPublisher.bitbucketCloud.data;

import java.util.Collection;

public class BitbucketCloudBuildStatuses {
  public final Collection<BitbucketCloudCommitBuildStatus> values;
  /**
   * Current number of objects on the existing page
   */
  public final int pagelen;
  /**
   * Page number of the current results
   */
  public final int page;
  /**
   * Total number of objects in the response (on all pages)
   */
  public final Integer size;

  public BitbucketCloudBuildStatuses(Collection<BitbucketCloudCommitBuildStatus> values, int pagelen, int page, int size) {
    this.values = values;
    this.pagelen = pagelen;
    this.page = page;
    this.size = size;
  }
}
