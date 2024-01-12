

package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.agent.Constants;

public class TfsConstants {
  public static final String ID = "tfs";
  public static final String AUTHENTICATION_TYPE = "tfsAuthType";

  public static final String AUTH_TYPE_TOKEN = "token";

  public static final String AUTH_TYPE_STORED_TOKEN = "storedToken";

  public static final String TOKEN_ID = "tokenId";
  public static final String ACCESS_TOKEN = Constants.SECURE_PROPERTY_PREFIX + "accessToken";
  public static final String AUTH_USER = "tfsAuthUser";
  public static final String AUTH_PROVIDER_ID = "tfsAuthProviderId";
  public static final String PUBLISH_PULL_REQUESTS = "publish.pull.requests";
  public static final String SERVER_URL = "tfsServerUrl";
  public static final String GIT_VCS_ROOT = "jetbrains.git";
  public static final String GIT_VCS_URL = "url";

  public String getAuthenticationTypeKey() {
    return AUTHENTICATION_TYPE;
  }

  public String getAuthTypeToken() {
    return AUTH_TYPE_TOKEN;
  }

  public String getAuthTypeStoredToken() {
    return AUTH_TYPE_STORED_TOKEN;
  }

  public String getTokenId() {
    return TOKEN_ID;
  }

  public String getAccessTokenKey() {
    return ACCESS_TOKEN;
  }

  public String getAuthUser() {
    return AUTH_USER;
  }

  public String getAuthProviderId() {
    return AUTH_PROVIDER_ID;
  }

  public String getPublishPullRequests() {
    return PUBLISH_PULL_REQUESTS;
  }

  public String getServerUrl() {
    return SERVER_URL;
  }
}