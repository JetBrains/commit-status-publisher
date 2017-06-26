package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.agent.Constants;

public class TfsConstants {
  public static final String ID = "tfs";
  public static final String AUTHENTICATION_TYPE = "tfsAuthType";
  public static final String ACCESS_TOKEN = Constants.SECURE_PROPERTY_PREFIX + "accessToken";
  public static final String AUTH_USER = "tfsAuthUser";
  public static final String AUTH_PROVIDER_ID = "tfsAuthProviderId";
  public static final String GIT_VCS_ROOT = "jetbrains.git";

  public String getAuthenticationTypeKey() {
    return AUTHENTICATION_TYPE;
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
}
