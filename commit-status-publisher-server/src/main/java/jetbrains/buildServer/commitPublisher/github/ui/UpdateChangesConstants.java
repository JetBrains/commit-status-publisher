

package jetbrains.buildServer.commitPublisher.github.ui;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 05.09.12 23:26
 */
public class UpdateChangesConstants {
  public String getServerKey() { return Constants.GITHUB_SERVER; }
  public String getUserNameKey() { return Constants.GITHUB_USERNAME; }
  public String getPasswordKey() { return Constants.GITHUB_PASSWORD; }
  public String getAccessTokenKey() { return Constants.GITHUB_TOKEN; }
  public String getOAuthUserKey() { return Constants.GITHUB_OAUTH_USER; }
  public String getOAuthProviderIdKey() { return Constants.GITHUB_OAUTH_PROVIDER_ID; }
  public String getAuthenticationTypeKey() { return Constants.GITHUB_AUTH_TYPE;}
  public String getAuthenticationTypePasswordValue() { return GitHubApiAuthenticationType.PASSWORD_AUTH.getValue();}
  public String getAuthenticationTypeTokenValue() { return GitHubApiAuthenticationType.TOKEN_AUTH.getValue();}
  public String getAuthentificationTypeGitHubAppTokenValue() {
    return GitHubApiAuthenticationType.STORED_TOKEN.getValue();
  }
  public String getTokenIdKey() {
    return Constants.TOKEN_ID;
  }

  public String getVcsRootId() {
    return Constants.VCS_ROOT_ID_PARAM;
  }

  public String getVcsAuthMethod() {
    return Constants.VCS_AUTH_METHOD;
  }
}