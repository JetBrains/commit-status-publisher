package jetbrains.buildServer.commitPublisher.upsource;

import org.jetbrains.annotations.NotNull;

public class UpsourceConstants {
  public static final String PUBLISHER_ID = "upsourcePublisher";
  public static final String SERVER_URL = "upsourceServerUrl";
  public static final String PROJECT_ID = "upsourceProjectId";
  public static final String USERNAME = "upsourceUsername";
  public static final String PASSWORD = "secure:upsourcePassword";

  @NotNull
  public String getServerUrl() {
    return SERVER_URL;
  }

  @NotNull
  public String getProjectId() {
    return PROJECT_ID;
  }

  @NotNull
  public String getUsername() {
    return USERNAME;
  }

  @NotNull
  public String getPassword() {
    return PASSWORD;
  }
}
