package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import org.jetbrains.annotations.NotNull;

public class BitbucketCloudConstants {
  public static final String PUBLISHER_ID = "bitbucketCloudPublisher";
  public static final String USERNAME = "bitbucketUsername";
  public static final String PASSWORD = "secure:bitbucketPassword";
  @NotNull
  public String getCloudUsername() {
    return USERNAME;
  }

  @NotNull
  public String getCloudPassword() {
    return PASSWORD;
  }
}
