package jetbrains.buildServer.commitPublisher;

import org.jetbrains.annotations.NotNull;

public class Constants {

  public static final String VCS_ROOT_ID_PARAM = "vcsRootId";
  public static final String PUBLISHER_ID_PARAM = "publisherId";

  public static final String PASSWORD_PARAMETER_TYPE = "password";
  public static final String GIT_VCS_NAME = "jetbrains.git";
  public static final String GIT_URL_PARAMETER = "url";

  public static final String UPSOURCE_SERVER_URL = "upsourceServerUrl";
  public static final String UPSOURCE_PROJECT_ID = "upsourceProjectId";
  public static final String UPSOURCE_USERNAME = "upsourceUsername";
  public static final String UPSOURCE_PASSWORD = "secure:upsourcePassword";

  @NotNull
  public String getVcsRootIdParam() {
    return VCS_ROOT_ID_PARAM;
  }

  @NotNull
  public String getPublisherIdParam() {
    return PUBLISHER_ID_PARAM;
  }

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
