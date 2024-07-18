package jetbrains.buildServer.commitPublisher.github;

public class GithubConstants {
  public static final String PUBLISHER_ID = "githubStatusPublisher";
  public static final String SERVER = "github_host";
  public static final String AUTH_TYPE = "github_authentication_type";
  public static final String USERNAME = "github_username";
  public static final String PASSWORD = "secure:github_password";
  public static final String PASSWORD_DEPRECATED = "github_password";
  public static final String TOKEN = "secure:github_access_token";
  public static final String OAUTH_USER = "github_oauth_user";
  public static final String OAUTH_PROVIDER_ID = "github_oauth_provider_id";
  public static final String CUSTOM_CONTEXT_BUILD_PARAM = "teamcity.commitStatusPublisher.githubContext";
  public static final String CONTEXT = "github_context";
}
