

package jetbrains.buildServer.commitPublisher.github.ui;

import jetbrains.buildServer.commitPublisher.github.GithubConstants;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 05.09.12 23:26
 */
public class UpdateChangesConstants {
  public String getServerKey() { return GithubConstants.GITHUB_SERVER; }
  public String getUserNameKey() { return GithubConstants.GITHUB_USERNAME; }
  public String getPasswordKey() { return GithubConstants.GITHUB_PASSWORD; }
  public String getAccessTokenKey() { return GithubConstants.GITHUB_TOKEN; }
  public String getOAuthUserKey() { return GithubConstants.GITHUB_OAUTH_USER; }
  public String getOAuthProviderIdKey() { return GithubConstants.GITHUB_OAUTH_PROVIDER_ID; }
  public String getAuthenticationTypeKey() { return GithubConstants.GITHUB_AUTH_TYPE;}
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