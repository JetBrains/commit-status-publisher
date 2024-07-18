

package jetbrains.buildServer.commitPublisher.space;

import org.jetbrains.annotations.NotNull;

public class SpaceConstants {

  public static final String PUBLISHER_ID = "spaceStatusPublisher";
  public static final String SERVER_URL = "spaceServerUrl";
  public static final String PROJECT_KEY = "spaceProjectKey";

  public static final String COMMIT_STATUS_PUBLISHER_DISPLAY_NAME = "spaceCommitStatusPublisherDisplayName";

  public static final String CONNECTION_ID = "spaceConnectionId";
  public static final String CREDENTIALS_TYPE = "spaceCredentialsType";
  public static final String CREDENTIALS_CONNECTION = "spaceCredentialsConnection";

  public static final String DEFAULT_DISPLAY_NAME = "TeamCity";

  public static final String UNCONDITIONAL_FEATURE_FORMAT = "SPACE_UNCONDITIONAL_PUBLISHER_BT_%s_VCSR_%s";

  public static final String DOMAIN_SPACE_CLOUD = "jetbrains.space";

  @NotNull
  public String getPublisherId() {
    return PUBLISHER_ID;
  }

  @NotNull
  public String getServerUrl() {
    return SERVER_URL;
  }

  @NotNull
  public String getProjectKey() {
    return PROJECT_KEY;
  }

  @NotNull
  public String getCommitStatusPublisherDisplayName() {
    return COMMIT_STATUS_PUBLISHER_DISPLAY_NAME;
  }

  @NotNull
  public String getConnectionId() {
    return CONNECTION_ID;
  }

  @NotNull
  public String getCredentialsType() {
    return CREDENTIALS_TYPE;
  }

  @NotNull
  public String getCredentialsConnection() {
    return CREDENTIALS_CONNECTION;
  }

}