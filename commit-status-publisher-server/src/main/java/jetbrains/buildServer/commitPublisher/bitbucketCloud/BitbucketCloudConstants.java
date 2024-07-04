package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import org.jetbrains.annotations.NotNull;

public class BitbucketCloudConstants {
  public static final String BITBUCKET_PUBLISHER_ID = "bitbucketCloudPublisher";
  public static final String BITBUCKET_CLOUD_USERNAME = "bitbucketUsername";
  public static final String BITBUCKET_CLOUD_PASSWORD = "secure:bitbucketPassword";
  @NotNull
  public String getBitbucketCloudUsername() {
    return BITBUCKET_CLOUD_USERNAME;
  }

  @NotNull
  public String getBitbucketCloudPassword() {
    return BITBUCKET_CLOUD_PASSWORD;
  }
}
