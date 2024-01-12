

package jetbrains.buildServer.commitPublisher.github;

import com.google.common.collect.Sets;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.api.SupportedVcsRootAuthentificationType;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.CommitStatus;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
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
  private final CommitStatusesCache<CommitStatus> myStatusesCache;
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

  public static final String GITHUB_OAUTH_PROVIDER_TYPE = "GitHub";
  public static final String GHE_OAUTH_PROVIDER_TYPE = "GHE";
  public static final String GITHUB_APP_OAUTH_PROVIDER_TYPE = "GitHubApp";

  public static final String CONNECTION_SUBTYPE = "connectionSubtype";

  public static final String ALL_IN_ONE_SUBTYPE = "gitHubApp";
  public static final String INSTALLATION_SUBTYPE = "gitHubAppInstallation";

  public static final Set<String> ALLOWED_SUBTYPES = Sets.newHashSet(ALL_IN_ONE_SUBTYPE, INSTALLATION_SUBTYPE);

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
    myStatusesCache = new CommitStatusesCache<>();
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
  public List<OAuthConnectionDescriptor> getOAuthConnections(final @NotNull SProject project, final @NotNull SUser user) {
    List<OAuthConnectionDescriptor> validConnections = new ArrayList<OAuthConnectionDescriptor>();
    validConnections.addAll(myOauthConnectionsManager.getAvailableConnectionsOfType(project, GITHUB_OAUTH_PROVIDER_TYPE));
    validConnections.addAll(myOauthConnectionsManager.getAvailableConnectionsOfType(project, GITHUB_APP_OAUTH_PROVIDER_TYPE)
                                                     .stream().filter(c -> ALLOWED_SUBTYPES.contains(c.getParameters().get(CONNECTION_SUBTYPE)))
                                                     .collect(Collectors.toList()));
    validConnections.addAll(myOauthConnectionsManager.getAvailableConnectionsOfType(project, GHE_OAUTH_PROVIDER_TYPE));
    return validConnections.stream()
      .sorted(CONNECTION_DESCRIPTOR_NAME_COMPARATOR)
      .collect(Collectors.toList());
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
    return new GitHubPublisher(this, buildType, buildFeatureId, myUpdater, params, myProblems, myLinks, myStatusesCache);
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
          p.remove(c.getTokenIdKey());
          p.remove(c.getOAuthUserKey());
          p.remove(c.getOAuthProviderIdKey());
        } else if (authenticationType == GitHubApiAuthenticationType.TOKEN_AUTH) {
          p.remove(c.getUserNameKey());
          p.remove(c.getPasswordKey());
          p.remove(c.getTokenIdKey());
          String oauthUsername = p.get(c.getOAuthUserKey());
          String oauthProviderId = p.get(c.getOAuthProviderIdKey());
          if (null != oauthUsername && null != oauthProviderId) {
            User currentUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
            if (null != currentUser && currentUser instanceof SUser) {
              for (OAuthToken token: myOAuthTokensStorage.getUserTokens(oauthProviderId, (SUser) currentUser, buildTypeOrTemplate.getProject(), false)) {
                if (token.getOauthLogin().equals(oauthUsername)) {
                  p.put(c.getAccessTokenKey(), token.getAccessToken());
                  p.remove(c.getOAuthProviderIdKey());
                  p.remove(c.getOAuthUserKey());
                  break;
                }
              }
            }
          }
          else {
            p.remove(c.getOAuthProviderIdKey());
            p.remove(c.getOAuthUserKey());
          }
          checkNotEmpty(p, c.getAccessTokenKey(), "Personal Access Token must be specified", result);
        } else if (authenticationType == GitHubApiAuthenticationType.STORED_TOKEN) {
          checkNotEmpty(p, c.getTokenIdKey(), "TokenId must be specified", result);
          checkNotEmpty(p, c.getVcsRootId(), "A VCS root must be selected to use this authentication type", result);
          p.remove(c.getAccessTokenKey());
          p.remove(c.getOAuthUserKey());
          p.remove(c.getUserNameKey());
          p.remove(c.getPasswordKey());
          p.remove(c.getOAuthProviderIdKey());
        } else if (authenticationType == GitHubApiAuthenticationType.VCS_ROOT) {
          List<SVcsRoot> roots = null;
          if (buildTypeOrTemplate instanceof BuildTypeSettings) {
            roots = ((BuildTypeSettings) buildTypeOrTemplate).getVcsRoots();
          }

          if (null == roots || roots.isEmpty()) {
            result.add(new InvalidProperty(c.getVcsRootId(), "No VCS Roots attached"));
          }
          else {
            if (p.containsKey(c.getVcsRootId())) {
              roots = roots.stream().filter(root -> root.getExternalId().equals(p.get(c.getVcsRootId()))).collect(Collectors.toList());
              if (roots.isEmpty()) {
                result.add(new InvalidProperty(c.getVcsRootId(), "Attached VCS Root (" + p.get(c.getVcsRootId()) + ") doesn't belong to the current build configuration"));
              }
            }

            for (VcsRoot vcsRoot : roots) {
              if (!SupportedVcsRootAuthentificationType.contains(vcsRoot.getProperty(c.getVcsAuthMethod()))) {
                result.add(new InvalidProperty(c.getAuthenticationTypeKey(), "Using " + vcsRoot.getProperty(c.getVcsAuthMethod()) + " authentication method in attached VCS Root (" + vcsRoot.getExternalId() +
                                                                             ") to extract statuses information is impossible. " +
                                                                             "Please provide an access token or GitHub App connection"));
              }
            }
          }

          p.remove(c.getAccessTokenKey());
          p.remove(c.getOAuthUserKey());
          p.remove(c.getUserNameKey());
          p.remove(c.getPasswordKey());
          p.remove(c.getOAuthProviderIdKey());
          p.remove(c.getTokenIdKey());
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
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  @NotNull
  @Override
  public Map<String, Object> getSpecificAttributes(@NotNull SProject project, @NotNull Map<String, String> params) {
    Map<String, Object> result = new HashMap<>();
    final boolean canEditProject = AuthUtil.hasPermissionToManageProject(mySecurityContext.getAuthorityHolder(), project.getProjectId());

    result.put("canEditProject", canEditProject);

    final boolean isAcquiringTokensEnable = TeamCityProperties.getBooleanOrTrue("teamcity.oauth.github.app.acquiring.token.buttons.enable");
    if (!isAcquiringTokensEnable) {
      result.put("isAcquiringTokensDisabled", true);
      return result;
    }

    final String tokenId = params.get(Constants.TOKEN_ID);
    if (StringUtil.isEmptyOrSpaces(tokenId)) {
      return result;
    }

    final OAuthToken token = myOAuthTokensStorage.getRefreshableToken(project, tokenId);
    if (token == null) {
      return result;
    }

    final TokenFullIdComponents tokenIdComponents = OAuthTokensStorage.parseFullTokenId(tokenId);
    if (tokenIdComponents == null) {
      return result;
    }

    final OAuthConnectionDescriptor connection = myOauthConnectionsManager.findConnectionByTokenStorageId(project, tokenIdComponents.getTokenStorageId());
    if (connection == null) {
      return result;
    }

    result.put("tokenConnection", connection.getConnectionDisplayName());

    return result;
  }

  @Nullable
  @Override
  public Map<String, Object> checkHealth(@NotNull SBuildType buildType, @NotNull Map<String, String> params) {
    final GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(params.get(Constants.GITHUB_AUTH_TYPE));
    if (authenticationType == GitHubApiAuthenticationType.STORED_TOKEN) {
      final String tokenId = params.get(Constants.TOKEN_ID);
      if (StringUtil.isEmptyOrSpaces(tokenId)) {
        return healthItemData("has authentication type set to GitHub App access token, but no token id is configured");
      }

      final OAuthToken token = myOAuthTokensStorage.getRefreshableToken(buildType.getProject(), tokenId);
      if (token == null) {
        return healthItemData("refers to a missing or invalid authentication token (token id: " +
                              tokenId +
                              "). Please check connection and authentication settings or try to acquire a new token.");
      }
    }

    return null;
  }

  @NotNull
  protected static Map<String, Object> healthItemData(String message) {
    final Map<String, Object> healthItemData = new HashMap<>();
    healthItemData.put("message", message);
    return healthItemData;
  }
}