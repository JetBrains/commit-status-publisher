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
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.tfs.TfsAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

/**
 * Settings for TFS Git commit status publisher.
 */
public class TfsPublisherSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private final OAuthConnectionsManager myOauthConnectionsManager;
  private final OAuthTokensStorage myOAuthTokensStorage;
  private final SecurityContext mySecurityContext;
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
                              @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
    myOauthConnectionsManager = oauthConnectionsManager;
    myOAuthTokensStorage = oauthTokensStorage;
    mySecurityContext = securityContext;
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
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final @NotNull SProject project, final @NotNull SUser user) {
    final List<OAuthConnectionDescriptor> tfsConnections = myOauthConnectionsManager.getAvailableConnectionsOfType(project, TfsAuthProvider.TYPE);
    final Map<OAuthConnectionDescriptor, Boolean> connections = new LinkedHashMap<OAuthConnectionDescriptor, Boolean>();
    for (OAuthConnectionDescriptor c : tfsConnections) {
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
      commitId = TfsStatusPublisher.getLatestCommitId(root, params, trustStore());
      if (commitId == null) {
        throw new PublisherException("No commits found in the repository");
      }
    }

    TfsStatusPublisher.testConnection(root, params, commitId, trustStore());
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
  @Override
  public Map<String, String> getDefaultParameters() {
    final Map<String, String> result = new HashMap<String, String>();
    result.put(TfsConstants.AUTHENTICATION_TYPE, "token");
    return result;
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

        final String authUsername = p.get(TfsConstants.AUTH_USER);
        final String authProviderId = p.get(TfsConstants.AUTH_PROVIDER_ID);
        if (authUsername != null && authProviderId != null) {
          final User currentUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
          if (currentUser != null && currentUser instanceof SUser) {
            for (OAuthToken token : myOAuthTokensStorage.getUserTokens(authProviderId, (SUser) currentUser, buildTypeOrTemplate.getProject(), false)) {
              if (token.getOauthLogin().equals(authUsername)) {
                p.put(TfsConstants.ACCESS_TOKEN, token.getAccessToken());
                p.remove(TfsConstants.AUTH_USER);
                p.remove(TfsConstants.AUTH_PROVIDER_ID);
              }
            }
          }
        }

        checkNotEmpty(p, TfsConstants.ACCESS_TOKEN, "Personal Access Token must be specified", result);

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
    return isBuildQueuedSupported(buildType, params) ? mySupportedEventsWithQueued : mySupportedEvents;
  }
}
