package jetbrains.buildServer.commitPublisher.stash;

import org.jetbrains.annotations.NotNull;

public class StashConstants {
  public static final String PUBLISHER_ID = "atlassianStashPublisher";
  public static final String BASE_URL = "stashBaseUrl";
  public static final String USERNAME = "stashUsername";
  public static final String PASSWORD = "secure:stashPassword";
  public static final String OAUTH_PROVIDER_TYPE = "BitbucketServer";

  @NotNull
  public String getBaseUrl() {
    return BASE_URL;
  }

  @NotNull
  public String getUsername() {
    return USERNAME;
  }

  @NotNull
  public String getPassword() {
    return PASSWORD;
  }

  @NotNull
  public String getOauthProviderType() {
    return OAUTH_PROVIDER_TYPE;
  }
  }
