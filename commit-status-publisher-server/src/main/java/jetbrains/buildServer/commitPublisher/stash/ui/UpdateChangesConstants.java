package jetbrains.buildServer.commitPublisher.stash.ui;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.stash.StashAuthenticationType;

public class UpdateChangesConstants {
  public String getStashBaseUrl() { return Constants.STASH_BASE_URL; }
  public String getStashUsername() { return Constants.STASH_USERNAME; }
  public String getStashPassword() { return Constants.STASH_PASSWORD; }
  public String getStashToken() { return Constants.STASH_TOKEN; }
  public String getAuthenticationTypeKey() { return Constants.STASH_AUTH_TYPE;}
  public String getAuthenticationTypePasswordValue() { return StashAuthenticationType.PASSWORD_AUTH.getValue();}
  public String getAuthenticationTypeTokenValue() { return StashAuthenticationType.TOKEN_AUTH.getValue();}
}
