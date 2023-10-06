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

package jetbrains.buildServer.commitPublisher.tfs;

import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.azuredevops.AzureDevOpsOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.tfs.TfsAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

/**
 * Settings for TFS Git commit status publisher.
 */
public class TfsPublisherSettings extends AuthTypeAwareSettings implements CommitStatusPublisherSettings {
  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.INTERRUPTED);
    add(Event.MARKED_AS_SUCCESSFUL);
  }};

  private static final Set<Event> mySupportedEventsWithQueued = new HashSet<Event>() {{
    add(Event.QUEUED);
    add(Event.REMOVED_FROM_QUEUE);
    addAll(mySupportedEvents);
  }};

  private final CommitStatusesCache<TfsStatusPublisher.CommitStatus> myStatusesCache;

  public TfsPublisherSettings(@NotNull PluginDescriptor descriptor,
                              @NotNull WebLinks links,
                              @NotNull CommitStatusPublisherProblems problems,
                              @NotNull OAuthConnectionsManager oauthConnectionsManager,
                              @NotNull OAuthTokensStorage oauthTokensStorage,
                              @NotNull SecurityContext securityContext,
                              @NotNull UserModel userModel,
                              @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider, oauthTokensStorage, userModel, oauthConnectionsManager, securityContext);
    myStatusesCache = new CommitStatusesCache<>();
  }

  @NotNull
  public String getId() {
    return TfsConstants.ID;
  }

  @NotNull
  public String getName() {
    return "Azure DevOps";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return "tfs/tfsSettings.jsp";
  }

  @NotNull
  @Override
  public List<OAuthConnectionDescriptor> getOAuthConnections(final @NotNull SProject project, final @NotNull SUser user) {
    return myOAuthConnectionsManager.getAvailableConnectionsOfType(project, AzureDevOpsOAuthProvider.TYPE).stream()
      .sorted(CONNECTION_DESCRIPTOR_NAME_COMPARATOR)
      .collect(Collectors.toList());
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String commitId = null;
    if (root instanceof VcsRootInstance) {
      final VcsRootInstance rootInstance = (VcsRootInstance)root;
      try {
        commitId = rootInstance.getCurrentRevision().getVersion();
      } catch (VcsException e) {
        LOG.infoAndDebugDetails("Failed to get current repository version", e);
      }
    }

    if (commitId == null) {
      commitId = TfsStatusPublisher.getLatestCommitId(root, params, trustStore(), getCredentials(root, params));
      if (commitId == null) {
        throw new PublisherException("No commits found in the repository");
      }
    }

    TfsStatusPublisher.testConnection(root, params, commitId, trustStore(), getCredentials(root, params));
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new TfsStatusPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, myStatusesCache);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return String.format("Post commit status to %s", getName());
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
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

        String authType = p.getOrDefault(TfsConstants.AUTHENTICATION_TYPE, TfsConstants.AUTH_TYPE_TOKEN);

        if (TfsConstants.AUTH_TYPE_TOKEN.equals(authType)) {
          //PAT token was set via magic button
          final String authUsername = p.get(TfsConstants.AUTH_USER);
          final String authProviderId = p.get(TfsConstants.AUTH_PROVIDER_ID);
          if (authUsername != null && authProviderId != null) {
            final User currentUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
            if (currentUser != null && currentUser instanceof SUser) {
              for (OAuthToken token : myOAuthTokensStorage.getUserTokens(authProviderId, (SUser)currentUser, buildTypeOrTemplate.getProject(), false)) {
                if (token.getOauthLogin().equals(authUsername)) {
                  p.put(TfsConstants.ACCESS_TOKEN, token.getAccessToken());
                  p.remove(TfsConstants.AUTH_USER);
                  p.remove(TfsConstants.AUTH_PROVIDER_ID);
                }
              }
            }
          }

          checkNotEmpty(p, TfsConstants.ACCESS_TOKEN, "Personal Access Token must be specified", result);
          p.remove(TfsConstants.TOKEN_ID);
        } else if (TfsConstants.AUTH_TYPE_STORED_TOKEN.equals(authType)) {
          checkNotEmpty(p, TfsConstants.TOKEN_ID, "No token configured", result);

          p.remove(TfsConstants.ACCESS_TOKEN);
          p.remove(TfsConstants.AUTH_USER);
          p.remove(TfsConstants.AUTH_PROVIDER_ID);
        } else {
          result.add(new InvalidProperty(TfsConstants.AUTHENTICATION_TYPE, "Unsupported authentication type"));
        }

        return result;
      }
    };
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot root) {
    return TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  @NotNull
  @Override
  protected String getDefaultAuthType() {
    return TfsConstants.AUTH_TYPE_TOKEN;
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
  protected String getAuthType(@NotNull Map<String, String> params) {
    return params.getOrDefault(TfsConstants.AUTHENTICATION_TYPE, getDefaultAuthType());
  }

  @NotNull
  @Override
  protected HttpCredentials getUsernamePasswordCredentials(@NotNull String username, @NotNull String password) throws PublisherException {
    throw new PublisherException("Unsupported authentication type username/password");
  }

  @NotNull
  @Override
  protected HttpCredentials getVcsRootCredentials(@Nullable VcsRoot root) throws PublisherException {
    throw new PublisherException("Unsupported authentication type VCS Root");
  }

  @NotNull
  @Override
  protected HttpCredentials getAccessTokenCredentials(@NotNull String token) throws PublisherException {
    return new UsernamePasswordCredentials(StringUtil.EMPTY, token);
  }

  @NotNull
  @Override
  protected HttpCredentials getAccessTokenCredentials(@NotNull final Map<String, String> params) throws PublisherException {
    final String token = params.get(TfsConstants.ACCESS_TOKEN);
    if (token == null) {
      throw new PublisherException("Azure DevOps access token is not defined");
    }
    return getAccessTokenCredentials(token);
  }

  @NotNull
  @Override
  public Map<String, Object> getSpecificAttributes(@NotNull SProject project, @NotNull Map<String, String> params) {
    Map<String, Object> specificAttributes = super.getSpecificAttributes(project, params);
    specificAttributes.put("azurePatConnections", myOAuthConnectionsManager.getAvailableConnectionsOfType(project, TfsAuthProvider.TYPE));
    return specificAttributes;
  }
}
