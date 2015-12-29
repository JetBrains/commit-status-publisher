package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.ssh.ServerSshKeyManager;
import org.jetbrains.annotations.NotNull;

public class Constants {

  public static final String VCS_ROOT_ID_PARAM = "vcsRootId";
  public static final String PUBLISHER_ID_PARAM = "publisherId";

  public static final String PASSWORD_PARAMETER_TYPE = "password";
  public static final String GIT_VCS_NAME = "jetbrains.git";
  public static final String GIT_URL_PARAMETER = "url";

  public static final String UPSOURCE_SERVER_URL = "upsourceServerUrl";
  public static final String UPSOURCE_PROJECT_ID = "upsourceProjectId";
  public static final String UPSOURCE_USERNAME = "upsourceUsername";
  public static final String UPSOURCE_PASSWORD = "secure:upsourcePassword";

  public static final String STASH_BASE_URL = "stashBaseUrl";
  public static final String STASH_USERNAME = "stashUsername";
  public static final String STASH_PASSWORD = "secure:stashPassword";

  public static final String GERRIT_SERVER = "gerritServer";
  public static final String GERRIT_PROJECT = "gerritProject";
  public static final String GERRIT_USERNAME = "gerritUsername";
  public static final String GERRIT_SUCCESS_VOTE = "successVote";
  public static final String GERRIT_FAILURE_VOTE = "failureVote";

  public static final String GITHUB_SERVER = "github_host";
  public static final String GITHUB_AUTH_TYPE = "github_authentication_type";
  public static final String GITHUB_USERNAME = "github_username";
  public static final String GITHUB_PASSWORD = "github_password";
  public static final String GITHUB_TOKEN = "secure:github_access_token";

  public static final String BITBUCKET_CLOUD_USERNAME = "bitbucketUsername";
  public static final String BITBUCKET_CLOUD_PASSWORD = "secure:bitbucketPassword";

  @NotNull
  public String getVcsRootIdParam() {
    return VCS_ROOT_ID_PARAM;
  }

  @NotNull
  public String getPublisherIdParam() {
    return PUBLISHER_ID_PARAM;
  }

  @NotNull
  public String getUpsourceServerUrl() {
    return UPSOURCE_SERVER_URL;
  }

  @NotNull
  public String getUpsourceProjectId() {
    return UPSOURCE_PROJECT_ID;
  }

  @NotNull
  public String getUpsourceUsername() {
    return UPSOURCE_USERNAME;
  }

  @NotNull
  public String getUpsourcePassword() {
    return UPSOURCE_PASSWORD;
  }

  @NotNull
  public String getSshKey() {
    return ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;
  }

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
  public String getGerritServer() {
    return GERRIT_SERVER;
  }

  @NotNull
  public String getGerritProject() {
    return GERRIT_PROJECT;
  }

  @NotNull
  public String getGerritUsername() {
    return GERRIT_USERNAME;
  }

  @NotNull
  public String getGerritSuccessVote() {
    return GERRIT_SUCCESS_VOTE;
  }

  @NotNull
  public String getGerritFailureVote() {
    return GERRIT_FAILURE_VOTE;
  }

  @NotNull
  public String getBitbucketCloudUsername() {
    return BITBUCKET_CLOUD_USERNAME;
  }

  @NotNull
  public String getBitbucketCloudPassword() {
    return BITBUCKET_CLOUD_PASSWORD;
  }
}
