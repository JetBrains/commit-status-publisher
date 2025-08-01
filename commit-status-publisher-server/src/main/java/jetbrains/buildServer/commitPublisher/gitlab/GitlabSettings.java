

/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.gitlab;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabReceiveCommitStatus;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabRepoInfo;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabUserInfo;
import jetbrains.buildServer.pullRequests.PullRequestManager;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabCEorEEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabClientImpl;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabComOAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsModificationHistoryEx;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitlabSettings extends AuthTypeAwareSettings implements CommitStatusPublisherSettings {

  private static final Pattern URL_WITH_API_SUFFIX = Pattern.compile("(.*)/api/v.");

  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

  private static final Set<Event> mySupportedEventsWithQueued = new HashSet<Event>() {{
    add(Event.QUEUED);
    add(Event.REMOVED_FROM_QUEUE);
    addAll(mySupportedEvents);
  }};

  @NotNull private final CommitStatusesCache<GitLabReceiveCommitStatus> myStatusesCache;
  @NotNull private final VcsModificationHistoryEx myVcsModificationHistory;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final GitLabBuildNameProvider myBuildNameProvider;

  public GitlabSettings(@NotNull PluginDescriptor descriptor,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull SSLTrustStoreProvider trustStoreProvider,
                        @NotNull VcsModificationHistoryEx vcsModificationHistory,
                        @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                        @NotNull OAuthTokensStorage oAuthTokensStorage,
                        @NotNull UserModel userModel,
                        @NotNull SecurityContext securityContext,
                        @NotNull ServiceLocator serviceLocator,
                        @NotNull GitLabBuildNameProvider buildNameProvider
  ) {
    super(descriptor, links, problems, trustStoreProvider, oAuthTokensStorage, userModel, oAuthConnectionsManager, securityContext);
    myVcsModificationHistory = vcsModificationHistory;
    myStatusesCache = new CommitStatusesCache<>();
    myServiceLocator = serviceLocator;
    myBuildNameProvider = buildNameProvider;
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public String getName() {
    return "GitLab";
  }

  @Nullable
  @Override
  public String getEditSettingsUrl() {
    return "gitlab/gitlabSettings.jsp";
  }

  @NotNull
  @Override
  public GitlabPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new GitlabPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, myStatusesCache, myVcsModificationHistory,
                               myServiceLocator.findSingletonService(PullRequestManager.class), myBuildNameProvider);
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String apiUrl = params.getOrDefault(Constants.GITLAB_API_URL, guessApiURL(root.getProperty("url")));
    if (StringUtil.isEmptyOrSpaces(apiUrl))
      throw new PublisherException("Missing GitLab API URL parameter");
    String pathPrefix = getPathPrefix(apiUrl);
    Repository repository = GitlabPublisher.parseRepository(root, pathPrefix);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());

    HttpCredentials credentials = getCredentials(buildTypeOrTemplate.getProject(), root, params);
    try {
      IOGuard.allowNetworkCall(() -> {
        ProjectInfoResponseProcessor processorPrj = new ProjectInfoResponseProcessor();
        HttpHelper.get(getProjectsUrl(apiUrl, repository.owner(), repository.repositoryName()),
                       credentials, Collections.emptyMap(),
                       BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processorPrj);
        boolean tokenHasEnoughRights = true;
        if (processorPrj.isEmptyPermissions()) {
          MultipleProjectsInfoResponseProcessor mulProjectProcessorPrj = new MultipleProjectsInfoResponseProcessor();
          HttpHelper.get(getAuthorizedProjectsUrl(apiUrl, repository.repositoryName()),
                         credentials, Collections.emptyMap(),
                         BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), mulProjectProcessorPrj);
          tokenHasEnoughRights = mulProjectProcessorPrj.getInfo().contains(processorPrj.getInfo());
        }
        else if (processorPrj.getAccessLevel() < 30) {
          UserInfoResponseProcessor processorUser = new UserInfoResponseProcessor();
          HttpHelper.get(getUserUrl(apiUrl), credentials, Collections.emptyMap(),
                         BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processorUser);
          tokenHasEnoughRights = processorUser.isAdmin();
        }
        if (!tokenHasEnoughRights) {
          throw new HttpPublisherException("GitLab does not grant enough permissions to publish a commit status");
        }
      });
    } catch (HttpPublisherException pe) {
      Integer statusCode = pe.getStatusCode();
      if (Objects.equals(statusCode, 404)) {
        throw new PublisherException(String.format("Repository \"%s\" can not be found. Please check if it was renamed or moved to another namespace", repository.repositoryName()));
      } else if (Objects.equals(statusCode, 401)) {
        throw new PublisherException(String.format("Cannot access the \"%s\" repository. Ensure you are using a valid access token instead of a password (authentication via password is no longer available)", repository.repositoryName()));
      } else {
        throw new PublisherException("Request was failed with error", pe);
      }
    } catch (Exception ex) {
      throw new PublisherException(String.format("GitLab publisher has failed to connect to \"%s\" repository", repository.url()), ex);
    }
  }

  @NotNull
  @Override
  protected String getDefaultAuthType() {
    return Constants.AUTH_TYPE_ACCESS_TOKEN;
  }

  @Nullable
  @Override
  protected String getUsername(@NotNull Map<String, String> params) {
    return null;
  }

  @Nullable
  @Override
  protected String getPassword(@NotNull Map<String, String> params) {
    return null;
  }

  @NotNull
  @Override
  protected HttpCredentials getUsernamePasswordCredentials(@NotNull String username, @NotNull String password) throws PublisherException {
    return new GitLabAccessTokenCredentials(password);
  }

  @NotNull
  @Override
  protected HttpCredentials getAccessTokenCredentials(@NotNull Map<String, String> params) throws PublisherException {
    final String token = params.get(Constants.GITLAB_TOKEN);
    if (token == null) {
      throw new PublisherException("GitLab Access token is not defined");
    }
    return new GitLabAccessTokenCredentials(token);
  }

  @NotNull
  @Override
  protected HttpCredentials getAccessTokenCredentials(@NotNull String token) throws PublisherException {
    return new GitLabAccessTokenCredentials(token);
  }

  @NotNull
  @Override
  protected HttpCredentials getStoredTokenCredentials(@NotNull String tokenId, @NotNull OAuthToken token, @NotNull VcsRoot root, @NotNull SProject project) throws PublisherException {
    return new GitLabAccessTokenCredentials(tokenId, token, myOAuthTokensStorage, project);
  }

  @NotNull
  @Override
  protected HttpCredentials getVcsRootCredentials(@Nullable VcsRoot root, @NotNull SProject project) throws PublisherException {
    return super.getVcsRootCredentials(root, project);
  }

  @Nullable
  public static String getPathPrefix(final String apiUrl) {
    if (!URL_WITH_API_SUFFIX.matcher(apiUrl).matches()) return null;
    try {
      URI uri = new URI(apiUrl);
      String path = uri.getPath();
      return path.substring(0, path.length() - "/api/v4".length());
    } catch (URISyntaxException e) {
      return null;
    }
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String result = super.describeParameters(params);
    String url = params.get(Constants.GITLAB_API_URL);
    if (url != null)
      result += " " + WebUtil.escapeXml(url);
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        final String authenticationType = params.getOrDefault(Constants.AUTH_TYPE, Constants.AUTH_TYPE_ACCESS_TOKEN);

        if (Constants.AUTH_TYPE_STORED_TOKEN.equalsIgnoreCase(authenticationType)) {
          if (StringUtil.isEmptyOrSpaces(params.get(Constants.TOKEN_ID))) {
            errors.add(new InvalidProperty(Constants.TOKEN_ID, "The refreshable token must be acquired"));
          }
        } else {
          params.remove(Constants.TOKEN_ID);
        }

        if (Constants.AUTH_TYPE_ACCESS_TOKEN.equalsIgnoreCase(authenticationType)) {
          if (StringUtil.isEmptyOrSpaces(params.get(Constants.GITLAB_TOKEN)))
            errors.add(new InvalidProperty(Constants.GITLAB_TOKEN, "Access token must be specified"));
        }
        else {
          params.remove(Constants.GITLAB_TOKEN);
        }

        return errors;
      }
    };
  }


  @Override
  @Nullable
  public String guessApiURL(@Nullable final String vcsRootUrl) {
    String hostUrl = super.guessApiURL(vcsRootUrl);
    if (hostUrl == null)
      return null;
    return hostUrl + GitLabClientImpl.API_PATH;
  }

  @NotNull
  public static String getProjectsUrl(@NotNull String apiUrl, @NotNull String owner, @NotNull String repo) {
    return apiUrl + "/projects/" + owner.replace(".", "%2E").replace("/", "%2F") + "%2F" + repo.replace(".", "%2E");
  }

  @NotNull
  public static String getAuthorizedProjectsUrl(@NotNull String apiUrl, @NotNull String repo) {
    return apiUrl + "/projects" + "?min_access_level=30&search=" + repo;
  }

  @NotNull
  public static String getUserUrl(@NotNull String apiUrl) {
    return apiUrl + "/user";
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  private abstract class JsonResponseProcessor<T> extends DefaultHttpResponseProcessor {

    private final Class<T> myInfoClass;
    private T myInfo;

    JsonResponseProcessor(Class<T> infoClass) {
      myInfoClass = infoClass;
    }

    T getInfo() {
      return myInfo;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {

      super.processResponse(response);

      final String json = response.getContent();
      if (null == json) {
        throw new HttpPublisherException("GitLab publisher has received no response");
      }
      myInfo = myGson.fromJson(json, myInfoClass);
    }
  }

  private abstract class JsonListResponseProcessor<T> extends DefaultHttpResponseProcessor {

    private final Type myInfoType;
    private List<T> myInfo;

    JsonListResponseProcessor(Class<T> infoClass) {
      myInfoType = TypeToken.getParameterized(List.class, infoClass).getType();
    }

    List<T> getInfo() {
      return myInfo;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {

      super.processResponse(response);

      final String json = response.getContent();
      if (null == json) {
        throw new HttpPublisherException("GitLab publisher has received no response");
      }
      myInfo = myGson.fromJson(json, myInfoType);
    }
  }

  private class ProjectInfoResponseProcessor extends JsonResponseProcessor<GitLabRepoInfo> {

    private int myAccessLevel;
    private boolean emptyPermissions;

    ProjectInfoResponseProcessor() {
      super(GitLabRepoInfo.class);
    }

    int getAccessLevel() {
      return myAccessLevel;
    }

    boolean isEmptyPermissions() {
      return emptyPermissions;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      myAccessLevel = 0;
      emptyPermissions = false;
      super.processResponse(response);
      GitLabRepoInfo repoInfo = getInfo();
      if (null == repoInfo || null == repoInfo.id || null == repoInfo.permissions) {
        throw new HttpPublisherException("GitLab publisher has received a malformed response");
      }
      if (null != repoInfo.permissions.project_access)
        myAccessLevel = repoInfo.permissions.project_access.access_level;
      if (null != repoInfo.permissions.group_access && myAccessLevel < repoInfo.permissions.group_access.access_level)
        myAccessLevel = repoInfo.permissions.group_access.access_level;
      if(null == repoInfo.permissions.project_access && null == repoInfo.permissions.group_access) {
        emptyPermissions = true;
      }
    }

  }

  private class MultipleProjectsInfoResponseProcessor extends JsonListResponseProcessor<GitLabRepoInfo> {

    MultipleProjectsInfoResponseProcessor() {
      super(GitLabRepoInfo.class);
    }
  }

  @NotNull
  @Override
  public List<OAuthConnectionDescriptor> getOAuthConnections(@NotNull SProject project, @NotNull SUser user) {
    List<OAuthConnectionDescriptor> validConnections = new ArrayList<OAuthConnectionDescriptor>();
    validConnections.addAll(myOAuthConnectionsManager.getAvailableConnectionsOfType(project, GitLabComOAuthProvider.TYPE));
    validConnections.addAll(myOAuthConnectionsManager.getAvailableConnectionsOfType(project, GitLabCEorEEOAuthProvider.TYPE));

    return validConnections;
  }

  private class UserInfoResponseProcessor extends JsonResponseProcessor<GitLabUserInfo> {

    private boolean myIsAdmin;

    UserInfoResponseProcessor() {
      super(GitLabUserInfo.class);
    }

    boolean isAdmin() {
      return myIsAdmin;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      myIsAdmin = false;
      super.processResponse(response);
      GitLabUserInfo userInfo = getInfo();
      if (null == userInfo || null == userInfo.id) {
        throw new HttpPublisherException("GitLab publisher has received a malformed response");
      }
      myIsAdmin = userInfo.is_admin;
    }
  }

}