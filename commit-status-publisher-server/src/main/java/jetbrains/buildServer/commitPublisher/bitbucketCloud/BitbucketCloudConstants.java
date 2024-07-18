package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import org.jetbrains.annotations.NotNull;

public class BitbucketCloudConstants {
  public static final String PUBLISHER_ID = "bitbucketCloudPublisher";
  public static final String CLOUD_USERNAME = "bitbucketUsername";
  public static final String CLOUD_PASSWORD = "secure:bitbucketPassword";
  @NotNull
  public String getCloudUsername() {
    return CLOUD_USERNAME;
  }

  @NotNull
  public String getCloudPassword() {
    return CLOUD_PASSWORD;
  }
}
