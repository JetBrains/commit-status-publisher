package jetbrains.buildServer.commitPublisher.tfs;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.serverSide.oauth.tfs.TfsAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Settings for TFS Git commit status publisher.
 */
public class TfsPublisherSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private static final Logger LOG = Logger.getInstance(TfsPublisherSettings.class.getName());
  private final OAuthConnectionsManager myOauthConnectionsManager;
  private final OAuthTokensStorage myOAuthTokensStorage;
  private final SecurityContext mySecurityContext;
  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.INTERRUPTED);
    add(Event.MARKED_AS_SUCCESSFUL);
  }};

  public TfsPublisherSettings(@NotNull ExecutorServices executorServices,
                              @NotNull PluginDescriptor descriptor,
                              @NotNull WebLinks links,
                              @NotNull CommitStatusPublisherProblems problems,
                              @NotNull OAuthConnectionsManager oauthConnectionsManager,
                              @NotNull OAuthTokensStorage oauthTokensStorage,
                              @NotNull SecurityContext securityContext) {
    super(executorServices, descriptor, links, problems);
    myOauthConnectionsManager = oauthConnectionsManager;
    myOAuthTokensStorage = oauthTokensStorage;
    mySecurityContext = securityContext;
  }

  @NotNull
  public String getId() {
    return TfsConstants.ID;
  }

  @NotNull
  public String getName() {
    return "Visual Studio Team Services";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return "tfs/tfsSettings.jsp";
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final SProject project, final SUser user) {
    final List<OAuthConnectionDescriptor> tfsConnections = myOauthConnectionsManager.getAvailableConnectionsOfType(project, TfsAuthProvider.TYPE);
    final Map<OAuthConnectionDescriptor, Boolean> connections = new LinkedHashMap<OAuthConnectionDescriptor, Boolean>();
    for (OAuthConnectionDescriptor c : tfsConnections) {
      connections.put(c, !myOAuthTokensStorage.getUserTokens(c.getId(), user).isEmpty());
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
      commitId = TfsStatusPublisher.getLatestCommitId(root, params);
    }

    TfsStatusPublisher.testConnection(root, params, commitId);
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new TfsStatusPublisher(this, buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems);
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
  public PropertiesProcessor getParametersProcessor() {
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
            for (OAuthToken token : myOAuthTokensStorage.getUserTokens(authProviderId, (SUser) currentUser)) {
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
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      return false;
    }

    final String url = root.getProperty("url");
    if (StringUtil.isEmptyOrSpaces(url)) {
      return false;
    }

    return TfsStatusPublisher.TFS_GIT_PROJECT_PATTERN.matcher(url).find();
  }

  @Override
  protected Set<Event> getSupportedEvents() {
    return mySupportedEvents;
  }
}
