package jetbrains.buildServer.commitPublisher.gitlab;

import org.jetbrains.annotations.NotNull;

public class GitlabConstants {
  public static final String PUBLISHER_ID = "gitlabStatusPublisher";
  public static final String API_URL = "gitlabApiUrl";
  public static final String TOKEN = "secure:gitlabAccessToken";
  public static final String FEATURE_TOGGLE_MERGE_RESULTS = "commitStatusPubliser.gitlab.supportMergeResults";

  @NotNull
  public String getPublisherId() {
    return PUBLISHER_ID;
  }

  @NotNull
  public String getServer() {
    return API_URL;
  }

  @NotNull
  public String getToken() {
    return TOKEN;
  }
}
