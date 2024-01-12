

package jetbrains.buildServer.commitPublisher.space;

import org.jetbrains.annotations.NotNull;

public class Constants {

  public static final String SPACE_PUBLISHER_ID = "spaceStatusPublisher";
  public static final String SPACE_SERVER_URL = "spaceServerUrl";
  public static final String SPACE_PROJECT_KEY = "spaceProjectKey";

  public static final String SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME = "spaceCommitStatusPublisherDisplayName";

  public static final String SPACE_CONNECTION_ID = "spaceConnectionId";
  public static final String SPACE_CREDENTIALS_TYPE = "spaceCredentialsType";
  public static final String SPACE_CREDENTIALS_CONNECTION = "spaceCredentialsConnection";

  public static final String SPACE_DEFAULT_DISPLAY_NAME = "TeamCity";

  public static final String SPACE_UNCONDITIONAL_FEATURE_FORMAT = "SPACE_UNCONDITIONAL_PUBLISHER_BT_%s_VCSR_%s";

  public static final String DOMAIN_SPACE_CLOUD = "jetbrains.space";

  @NotNull
  public String getSpacePublisherId() {
    return SPACE_PUBLISHER_ID;
  }

  @NotNull
  public String getSpaceServerUrl() {
    return SPACE_SERVER_URL;
  }

  @NotNull
  public String getSpaceProjectKey() {
    return SPACE_PROJECT_KEY;
  }

  @NotNull
  public String getSpaceCommitStatusPublisherDisplayName() {
    return SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME;
  }

  @NotNull
  public String getSpaceConnectionId() {
    return SPACE_CONNECTION_ID;
  }

  @NotNull
  public String getSpaceCredentialsType() {
    return SPACE_CREDENTIALS_TYPE;
  }

  @NotNull
  public String getSpaceCredentialsConnection() {
    return SPACE_CREDENTIALS_CONNECTION;
  }

}