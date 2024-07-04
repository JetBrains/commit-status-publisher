package jetbrains.buildServer.commitPublisher.stash;

import org.jetbrains.annotations.NotNull;

public class StashConstants {
  public static final String STASH_PUBLISHER_ID = "atlassianStashPublisher";
  public static final String STASH_BASE_URL = "stashBaseUrl";
  public static final String STASH_USERNAME = "stashUsername";
  public static final String STASH_PASSWORD = "secure:stashPassword";
  public static final String STASH_OAUTH_PROVIDER_TYPE = "BitbucketServer";

  @NotNull
  public String getStashBaseUrl() {
    return STASH_BASE_URL;
  }

  @NotNull
  public String getStashUsername() {
    return STASH_USERNAME;
  }

  @NotNull
  public String getStashPassword() {
    return STASH_PASSWORD;
  }

  @NotNull
  public String getStashOauthProviderType() {
    return STASH_OAUTH_PROVIDER_TYPE;
  }
  }
