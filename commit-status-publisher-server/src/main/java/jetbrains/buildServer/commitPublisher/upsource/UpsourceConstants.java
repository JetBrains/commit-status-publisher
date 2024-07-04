package jetbrains.buildServer.commitPublisher.upsource;

import org.jetbrains.annotations.NotNull;

public class UpsourceConstants {
  public static final String UPSOURCE_PUBLISHER_ID = "upsourcePublisher";
  public static final String UPSOURCE_SERVER_URL = "upsourceServerUrl";
  public static final String UPSOURCE_PROJECT_ID = "upsourceProjectId";
  public static final String UPSOURCE_USERNAME = "upsourceUsername";
  public static final String UPSOURCE_PASSWORD = "secure:upsourcePassword";

  @NotNull
  public String getUpsourceServerUrl() {
    return UPSOURCE_SERVER_URL;
  }

  @NotNull
  public String getUpsourceProjectId() {
    return UPSOURCE_PROJECT_ID;
  }

  @NotNull
  public String getUpsourceUsername() {
    return UPSOURCE_USERNAME;
  }

  @NotNull
  public String getUpsourcePassword() {
    return UPSOURCE_PASSWORD;
  }
}
