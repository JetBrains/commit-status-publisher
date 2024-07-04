package jetbrains.buildServer.commitPublisher.gitea;

import org.jetbrains.annotations.NotNull;

public class GiteaConstants {
  public static final String GITEA_PUBLISHER_ID = "giteaStatusPublisher";
  public static final String GITEA_API_URL = "giteaApiUrl";
  public static final String GITEA_USERNAME = "giteaUsername";
  public static final String GITEA_PASSWORD = "secure:giteaPassword";
  public static final String GITEA_TOKEN = "secure:giteaAccessToken";


  @NotNull
  public String getGiteaToken() {
    return GITEA_TOKEN;
  }

  @NotNull
  public String getGiteaServer() {
    return GITEA_API_URL;
  }

}
