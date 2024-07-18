package jetbrains.buildServer.commitPublisher.gitea;

import org.jetbrains.annotations.NotNull;

public class GiteaConstants {
  public static final String STATUS_PUBLISHER = "giteaStatusPublisher";
  public static final String API_URL = "giteaApiUrl";
  public static final String USERNAME = "giteaUsername";
  public static final String PASSWORD = "secure:giteaPassword";
  public static final String TOKEN = "secure:giteaAccessToken";


  @NotNull
  public String getToken() {
    return TOKEN;
  }

  @NotNull
  public String getServer() {
    return API_URL;
  }

}
