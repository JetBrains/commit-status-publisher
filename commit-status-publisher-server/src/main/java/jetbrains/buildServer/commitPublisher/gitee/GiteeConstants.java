package jetbrains.buildServer.commitPublisher.gitee;

import org.jetbrains.annotations.NotNull;

public class GiteeConstants {
  public static final String GITEE_PUBLISHER_ID = "giteeStatusPublisher";
  public static final String GITEE_API_URL = "giteeApiUrl";
  public static final String GITEE_USERNAME = "giteeUsername";
  public static final String GITEE_PASSWORD = "secure:giteePassword";
  public static final String GITEE_TOKEN = "secure:giteeAccessToken";

  @NotNull
  public String getGiteeToken() {
    return GITEE_TOKEN;
  }

  @NotNull
  public String getGiteeServer() {
    return GITEE_API_URL;
  }

}
