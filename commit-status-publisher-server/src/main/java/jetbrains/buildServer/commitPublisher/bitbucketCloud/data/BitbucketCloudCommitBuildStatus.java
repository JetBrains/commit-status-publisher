package jetbrains.buildServer.commitPublisher.bitbucketCloud.data;

public class BitbucketCloudCommitBuildStatus {
  public final String key;
  public final String state;
  public final String name;
  public final String description;
  public final String url;


  public BitbucketCloudCommitBuildStatus(String key,
                                         String state,
                                         String name,
                                         String description,
                                         String url) {
    this.key = key;
    this.state = state;
    this.name = name;
    this.description = description;
    this.url = url;
  }
}
