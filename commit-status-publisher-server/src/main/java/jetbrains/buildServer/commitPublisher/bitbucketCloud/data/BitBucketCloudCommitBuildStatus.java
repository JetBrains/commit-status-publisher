package jetbrains.buildServer.commitPublisher.bitbucketCloud.data;

import jetbrains.buildServer.commitPublisher.bitbucketCloud.BitbucketCloudBuildStatus;

public class BitBucketCloudCommitBuildStatus {
  public String key;
  public String url;
  public String name;
  public String description;
  public BitbucketCloudBuildStatus state;
}
