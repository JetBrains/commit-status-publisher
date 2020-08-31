package jetbrains.buildServer.commitPublisher.space;

import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT;

public class SpaceSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  static final String CHANGES_FIELD = "changes";
  static final String EXECUTION_STATUS_FIELD = "executionStatus";
  static final String BUILD_URL_FIELD = "url";
  static final String EXTERNAL_SERVICE_NAME_FIELD = "externalServiceName";
  static final String TASK_NAME_FIELD = "taskName";
  static final String TASK_ID_FIELD = "taskId";
  static final String TIMESTAMP_FIELD = "timestamp";
  static final String DESCRIPTION_FIELD = "description";

  private final OAuthConnectionsManager myOAuthConnectionManager;
  private final OAuthTokensStorage myOAuthTokensStorage;

  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

  public SpaceSettings(@NotNull ExecutorServices executorServices,
                       @NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems,
                       @NotNull SSLTrustStoreProvider trustStoreProvider,
                       @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                       @NotNull OAuthTokensStorage oauthTokensStorage) {
    super(executorServices, descriptor, links, problems, trustStoreProvider);
    myOAuthConnectionManager = oAuthConnectionsManager;
    myOAuthTokensStorage = oauthTokensStorage;
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.SPACE_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public String getName() {
    return "JetBrains Space";
  }

  @Nullable
  @Override
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("space/spaceSettings.jsp");
  }

  @Nullable
  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    SpaceConnectDescriber connector = SpaceUtils.getConnectionData(params, myOAuthConnectionManager, buildType.getProject());
    return new SpacePublisher(this, buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems, connector);
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    StringBuilder sb = new StringBuilder(super.describeParameters(params));
    String credentialsType = params.get(Constants.SPACE_CREDENTIALS_TYPE);

    if (credentialsType == null)
      sb.append(" with no credentials type provided!");
    else {
      switch (credentialsType) {
        case Constants.SPACE_CREDENTIALS_CONNECTION:
          sb.append(" using JetBrains Space connection");
          break;
      }
    }

    String projectKey = params.get(Constants.SPACE_PROJECT_KEY);
    String publisherDisplayName = getDisplayName(params);
    if (!StringUtil.isEmpty(projectKey)) {
      sb.append("\nProject key: ");
      sb.append(WebUtil.escapeXml(projectKey));
    }
    sb.append("\nPublisher display name: ");
    sb.append(WebUtil.escapeXml(publisherDisplayName));
    return sb.toString();
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return params -> {
      List<InvalidProperty> errors = new ArrayList<>();

      checkContains(params, Constants.SPACE_CREDENTIALS_TYPE, "JetBrains Space credentials type", errors);

      String credentialsType = params.get(Constants.SPACE_CREDENTIALS_TYPE);
      if (StringUtil.areEqual(credentialsType, Constants.SPACE_CREDENTIALS_CONNECTION)) {
        checkContains(params, Constants.SPACE_CONNECTION_ID, "JetBrains Space connection", errors);
      }
      return errors;
    };
  }

  private void checkContains(@NotNull Map<String, String> params, @NotNull String key, @NotNull String fieldName, @NotNull List<InvalidProperty> errors) {
    if (StringUtil.isEmpty(params.get(key)))
      errors.add(new InvalidProperty(key, String.format("%s must be specified", fieldName)));
  }

  @Override
  public boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.commitStatusPublisher.spaceEnabled");
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {

    SpaceConnectDescriber connector;
    try {
      connector = SpaceUtils.getConnectionData(params, myOAuthConnectionManager, buildTypeOrTemplate.getProject());
    } catch (IllegalArgumentException exc) {
      throw new PublisherException(exc.getMessage());
    }

    String serverUrl = HttpHelper.stripTrailingSlash(connector.getFullAddress());
    String serviceId = connector.getServiceId();
    String serviceSecret = connector.getServiceSecret();

    Repository repository = SpaceUtils.getRepositoryInfo(root, params.get(Constants.SPACE_PROJECT_KEY));

    try {
      SpaceToken token = SpaceToken.requestToken(serviceId, serviceSecret, serverUrl, DEFAULT_CONNECTION_TIMEOUT, myGson, trustStore());
      String url = SpaceApiUrls.commitStatusTestConnectionUrl(serverUrl, repository.owner());

      Map<String, String> headers = new LinkedHashMap<>();
      headers.put(HttpHeaders.ACCEPT, ContentType.TEXT_PLAIN.getMimeType());
      token.toHeader(headers);

      IOGuard.allowNetworkCall(() ->
        HttpHelper.post(
          url, null, null, null, ContentType.APPLICATION_JSON,
          headers, DEFAULT_CONNECTION_TIMEOUT, trustStore(), new ContentResponseProcessor()
        )
      );

    } catch (Exception ex) {
      throw new PublisherException("JetBrains Space publisher has failed to test connection", ex);
    }
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return mySupportedEvents;
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(SProject project, SUser user) {
    final Map<OAuthConnectionDescriptor, Boolean> connections = new HashMap<>();
    final List<OAuthConnectionDescriptor> spaceConnections = myOAuthConnectionManager.getAvailableConnectionsOfType(project, SpaceOAuthProvider.TYPE);

    for (OAuthConnectionDescriptor c : spaceConnections) {
      connections.put(c, !myOAuthTokensStorage.getUserTokens(c.getId(), user).isEmpty());
    }

    return connections;
  }

  @NotNull
  static String getDisplayName(@NotNull Map<String, String> params) {
    String displayName = params.get(Constants.SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME);
    return displayName == null ? Constants.SPACE_DEFAULT_DISPLAY_NAME : displayName;
  }
}
