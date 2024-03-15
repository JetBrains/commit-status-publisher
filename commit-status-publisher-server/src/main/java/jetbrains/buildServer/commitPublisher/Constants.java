

package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.ssh.ServerSshKeyManager;
import org.jetbrains.annotations.NotNull;

public class Constants {

  public final static String COMMIT_STATUS_PUBLISHER_PROBLEM_TYPE = "COMMIT_STATUS_PUBLISHER_PROBLEM";

  public static final String VCS_ROOT_ID_PARAM = "vcsRootId";
  public static final String PUBLISHER_ID_PARAM = "publisherId";

  public static final String PASSWORD = "password";
  public static final String TEST_CONNECTION_PARAM = "testconnection";
  public static final String TEST_CONNECTION_YES = "yes";
  public static final String GIT_VCS_NAME = "jetbrains.git";
  public static final String GIT_URL_PARAMETER = "url";

  public static final String AUTH_TYPE = "authType";
  public static final String AUTH_TYPE_ACCESS_TOKEN = "token";
  public static final String AUTH_TYPE_STORED_TOKEN = "storedToken";

  public static final String AUTH_TYPE_VCS = "vcsRoot";
  public static final String TOKEN_ID = "tokenId";
  public static final String VCS_AUTH_METHOD = "authMethod";

  public static final String UPSOURCE_PUBLISHER_ID = "upsourcePublisher";
  public static final String UPSOURCE_SERVER_URL = "upsourceServerUrl";
  public static final String UPSOURCE_PROJECT_ID = "upsourceProjectId";
  public static final String UPSOURCE_USERNAME = "upsourceUsername";
  public static final String UPSOURCE_PASSWORD = "secure:upsourcePassword";

  public static final String STASH_PUBLISHER_ID = "atlassianStashPublisher";
  public static final String STASH_BASE_URL = "stashBaseUrl";
  public static final String STASH_USERNAME = "stashUsername";
  public static final String STASH_PASSWORD = "secure:stashPassword";
  public static final String STASH_OAUTH_PROVIDER_TYPE = "BitbucketServer";

  public static final String GERRIT_PUBLISHER_ID = "gerritStatusPublisher";
  public static final String GERRIT_SERVER = "gerritServer";
  public static final String GERRIT_PROJECT = "gerritProject";
  public static final String GERRIT_USERNAME = "gerritUsername";
  public static final String GERRIT_SUCCESS_VOTE = "successVote";
  public static final String GERRIT_FAILURE_VOTE = "failureVote";
  public static final String GERRIT_LABEL = "label";

  public static final String GITHUB_PUBLISHER_ID = "githubStatusPublisher";
  public static final String GITHUB_SERVER = "github_host";
  public static final String GITHUB_AUTH_TYPE = "github_authentication_type";
  public static final String GITHUB_USERNAME = "github_username";
  public static final String GITHUB_PASSWORD = "secure:github_password";
  public static final String GITHUB_PASSWORD_DEPRECATED = "github_password";
  public static final String GITHUB_TOKEN = "secure:github_access_token";
  public static final String GITHUB_OAUTH_USER = "github_oauth_user";
  public static final String GITHUB_OAUTH_PROVIDER_ID = "github_oauth_provider_id";
  public static final String GITHUB_CUSTOM_CONTEXT_BUILD_PARAM = "teamcity.commitStatusPublisher.githubContext";
  public static final String GITHUB_CONTEXT = "github_context";

  public static final String BITBUCKET_PUBLISHER_ID = "bitbucketCloudPublisher";
  public static final String BITBUCKET_CLOUD_USERNAME = "bitbucketUsername";
  public static final String BITBUCKET_CLOUD_PASSWORD = "secure:bitbucketPassword";

  public static final String GITLAB_PUBLISHER_ID = "gitlabStatusPublisher";
  public static final String GITLAB_API_URL = "gitlabApiUrl";
  public static final String GITLAB_TOKEN = "secure:gitlabAccessToken";
  public static final String STATUSES_TO_LOAD_THRESHOLD_PROPERTY = "teamcity.commitStatusPubliser.statusesToLoad.threshold";
  public static final int STATUSES_TO_LOAD_THRESHOLD_DEFAULT_VAL = 50;
  public static final String GITLAB_FEATURE_TOGGLE_MERGE_RESULTS = "commitStatusPubliser.gitlab.supportMergeResults";

  public static final String GITEA_PUBLISHER_ID = "giteaStatusPublisher";
  public static final String GITEA_API_URL = "giteaApiUrl";
  public static final String GITEA_TOKEN = "secure:giteaAccessToken";

  @NotNull
  public String getVcsRootIdParam() {
    return VCS_ROOT_ID_PARAM;
  }

  @NotNull
  public String getPublisherIdParam() {
    return PUBLISHER_ID_PARAM;
  }

  @NotNull
  public String getAuthType() {
    return AUTH_TYPE;
  }

  @NotNull
  public String getAuthTypePassword() {
    return PASSWORD;
  }

  @NotNull
  public String getAuthTypeStoredToken() {
    return AUTH_TYPE_STORED_TOKEN;
  }

  @NotNull
  public String getAuthTypeVCS() {
    return AUTH_TYPE_VCS;
  }

  @NotNull
  public String getTokenId() {
    return TOKEN_ID;
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
  public String getStashOauthProviderType() {
    return STASH_OAUTH_PROVIDER_TYPE;
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
  public String getGerritLabel() {
    return GERRIT_LABEL;
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

  @NotNull
  public String getGiteaPublisherId() {
    return GITEA_PUBLISHER_ID;
  }

  @NotNull
  public String getGiteaServer() {
    return GITEA_API_URL;
  }

  @NotNull
  public String getGiteaToken() {
    return GITEA_TOKEN;
  }
}
