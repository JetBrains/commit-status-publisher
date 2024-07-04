package jetbrains.buildServer.commitPublisher.github;

public class GithubConstants {
  public static final String GITHUB_PUBLISHER_ID = "githubStatusPublisher";
  public static final String GITHUB_SERVER = "github_host";
  public static final String GITHUB_AUTH_TYPE = "github_authentication_type";
  public static final String GITHUB_USERNAME = "github_username";
  public static final String GITHUB_PASSWORD = "secure:github_password";
  public static final String GITHUB_PASSWORD_DEPRECATED = "github_password";
  public static final String GITHUB_TOKEN = "secure:github_access_token";
  public static final String GITHUB_OAUTH_USER = "github_oauth_user";
  public static final String GITHUB_OAUTH_PROVIDER_ID = "github_oauth_provider_id";
  public static final String GITHUB_CUSTOM_CONTEXT_BUILD_PARAM = "teamcity.commitStatusPublisher.githubContext";
  public static final String GITHUB_CONTEXT = "github_context";
}
