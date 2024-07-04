package jetbrains.buildServer.commitPublisher.gitlab;

import org.jetbrains.annotations.NotNull;

public class GitlabConstants {
  public static final String GITLAB_PUBLISHER_ID = "gitlabStatusPublisher";
  public static final String GITLAB_API_URL = "gitlabApiUrl";
  public static final String GITLAB_TOKEN = "secure:gitlabAccessToken";
  public static final String GITLAB_FEATURE_TOGGLE_MERGE_RESULTS = "commitStatusPubliser.gitlab.supportMergeResults";

  @NotNull
  public String getGitlabPublisherId() {
    return GITLAB_PUBLISHER_ID;
  }

  @NotNull
  public String getGitlabServer() {
    return GITLAB_API_URL;
  }

  @NotNull
  public String getGitlabToken() {
    return GITLAB_TOKEN;
  }
}
