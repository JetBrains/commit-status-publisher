/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.github;

import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.github.GHEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.github.GitHubOAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitHubSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private final ChangeStatusUpdater myUpdater;
  private final OAuthConnectionsManager myOauthConnectionsManager;
  private final OAuthTokensStorage myOAuthTokensStorage;
  private final SecurityContext mySecurityContext;
  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.INTERRUPTED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.FAILURE_DETECTED);
  }};

  private static final Set<Event> mySupportedEventsWithQueued = new HashSet<Event>() {{
    add(Event.QUEUED);
    add(Event.REMOVED_FROM_QUEUE);
    addAll(mySupportedEvents);
  }};

  public GitHubSettings(@NotNull ChangeStatusUpdater updater,
                        @NotNull PluginDescriptor descriptor,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull OAuthConnectionsManager oauthConnectionsManager,
                        @NotNull OAuthTokensStorage oauthTokensStorage,
                        @NotNull SecurityContext securityContext,
                        @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
    myUpdater = updater;
    myOauthConnectionsManager = oauthConnectionsManager;
    myOAuthTokensStorage = oauthTokensStorage;
    mySecurityContext = securityContext;
  }

  @NotNull
  public String getId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "GitHub";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return "github/githubSettings.jsp";
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    final Map<String, String> result = new HashMap<String, String>();
    final UpdateChangesConstants C = new UpdateChangesConstants();
    result.put(C.getServerKey(), GitHubApiFactory.DEFAULT_URL);
    return result;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    String securePwd = params.get(Constants.GITHUB_PASSWORD);
    String deprecatedPwd = params.get(Constants.GITHUB_PASSWORD_DEPRECATED);
    if (securePwd == null && deprecatedPwd != null) {
      Map<String, String> result = new HashMap<String, String>(params);
      result.remove(Constants.GITHUB_PASSWORD_DEPRECATED);
      result.put(Constants.GITHUB_PASSWORD, deprecatedPwd);
      return result;
    }
    return null;
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final @NotNull SProject project, final @NotNull SUser user) {
    List<OAuthConnectionDescriptor> validConnections = new ArrayList<OAuthConnectionDescriptor>();
    List<OAuthConnectionDescriptor> githubConnections = myOauthConnectionsManager.getAvailableConnectionsOfType(project, GitHubOAuthProvider.TYPE);
    if (!githubConnections.isEmpty()) {
      validConnections.add(githubConnections.get(0));
    }
    validConnections.addAll(myOauthConnectionsManager.getAvailableConnectionsOfType(project, GHEOAuthProvider.TYPE));
    Map<OAuthConnectionDescriptor, Boolean> connections = new LinkedHashMap<OAuthConnectionDescriptor, Boolean>();
    for (OAuthConnectionDescriptor c: validConnections) {
      connections.put(c, !myOAuthTokensStorage.getUserTokens(c.getId(), user, project, false).isEmpty());
    }
    return connections;
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    myUpdater.testConnection(root, params);
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new GitHubPublisher(this, buildType, buildFeatureId, myUpdater, params, myProblems, myLinks);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    String result = super.describeParameters(params);
    String url = params.get(Constants.GITHUB_SERVER);
    if (null != url && !url.equals(GitHubApiFactory.DEFAULT_URL)) {
      result += ": " + WebUtil.escapeXml(url);
    }
    return result;
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    final UpdateChangesConstants c = new UpdateChangesConstants();
    return new PropertiesProcessor() {
      private boolean checkNotEmpty(@NotNull final Map<String, String> properties,
                                    @NotNull final String key,
                                    @NotNull final String message,
                                    @NotNull final Collection<InvalidProperty> res) {
        if (isEmpty(properties, key)) {
          res.add(new InvalidProperty(key, message));
          return true;
        }
        return false;
      }

      private boolean isEmpty(@NotNull final Map<String, String> properties,
                              @NotNull final String key) {
        return StringUtil.isEmptyOrSpaces(properties.get(key));
      }

      @NotNull
      public Collection<InvalidProperty> process(@Nullable final Map<String, String> p) {
        final Collection<InvalidProperty> result = new ArrayList<InvalidProperty>();
        if (p == null) return result;

        GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(p.get(c.getAuthenticationTypeKey()));
        if (authenticationType == GitHubApiAuthenticationType.PASSWORD_AUTH) {
          checkNotEmpty(p, c.getUserNameKey(), "Username must be specified", result);
          checkNotEmpty(p, c.getPasswordKey(), "Password must be specified", result);
          p.remove(c.getAccessTokenKey());
        } else if (authenticationType == GitHubApiAuthenticationType.TOKEN_AUTH) {
          p.remove(c.getUserNameKey());
          p.remove(c.getPasswordKey());
          String oauthUsername = p.get(c.getOAuthUserKey());
          String oauthProviderId = p.get(c.getOAuthProviderIdKey());
          if (null != oauthUsername && null != oauthProviderId) {
            User currentUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
            if (null != currentUser && currentUser instanceof SUser) {
              for (OAuthToken token: myOAuthTokensStorage.getUserTokens(oauthProviderId, (SUser) currentUser, buildTypeOrTemplate.getProject(), false)) {
                if (token.getOauthLogin().equals(oauthUsername)) {
                  p.put(c.getAccessTokenKey(), token.getAccessToken());
                  p.remove(c.getOAuthProviderIdKey());
                }
              }
            }
          }
          checkNotEmpty(p, c.getAccessTokenKey(), "Personal Access Token must be specified", result);
        }

        if (!checkNotEmpty(p, c.getServerKey(), "GitHub API URL must be specified", result)) {
          final String url = "" + p.get(c.getServerKey());
          if (!ReferencesResolverUtil.mayContainReference(url) && !(url.startsWith("http://") || url.startsWith("https://"))) {
            result.add(new InvalidProperty(c.getServerKey(), "GitHub API URL should start with http:// or https://"));
          }
        }

        return result;
      }
    };
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType, params) ? mySupportedEventsWithQueued : mySupportedEvents;
  }
}
