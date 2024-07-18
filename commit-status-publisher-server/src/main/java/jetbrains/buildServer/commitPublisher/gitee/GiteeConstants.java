package jetbrains.buildServer.commitPublisher.gitee;

import org.jetbrains.annotations.NotNull;

public class GiteeConstants {
  public static final String STATUS_PUBLISHER = "giteeStatusPublisher";
  public static final String API_URL = "giteeApiUrl";
  public static final String USERNAME = "giteeUsername";
  public static final String PASSWORD = "secure:giteePassword";
  public static final String TOKEN = "secure:giteeAccessToken";

  @NotNull
  public String getToken() {
    return TOKEN;
  }

  @NotNull
  public String getServer() {
    return API_URL;
  }

}
