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
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpaceSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  static final String PROJECT_KEY_FIELD = "project";
  static final String REPOSITORY_FIELD = "repository";
  static final String REVISION_FIELD = "revision";
  static final String CHANGES_FIELD = "changes";
  static final String EXECUTION_STATUS_FIELD = "executionStatus";
  static final String BUILD_URL_FIELD = "url";
  static final String EXTERNAL_SERVICE_NAME_FIELD = "externalServiceName";
  static final String TASK_NAME_FIELD = "taskName";
  static final String TASK_ID_FIELD = "taskId";
  static final String TIMESTAMP_FIELD = "timestamp";
  static final String DESCRIPTION_FIELD = "description";

  static private final String ENDPOINT_COMMIT_STATUS_SERVICE = "http_api/CommitStatusService";
  static final String ENDPOINT_ADD_STATUS = ENDPOINT_COMMIT_STATUS_SERVICE + "/addStatus";
  static final String ENDPOINT_CHECK_SERVICE = ENDPOINT_COMMIT_STATUS_SERVICE + "/checkService";

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

        case Constants.SPACE_CREDENTIALS_USER:
          String serverUrl = params.get(Constants.SPACE_SERVER_URL);
          if (serverUrl != null) {
            sb.append(": ");
            sb.append(WebUtil.escapeXml(serverUrl));

          }
          break;
      }
    }

    String projectKey = params.get(Constants.SPACE_PROJECT_KEY);
    String publisherDisplayName = params.get(Constants.SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME);
    sb.append("\nProject key: ");
    sb.append(WebUtil.escapeXml(projectKey));
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

      if (StringUtil.areEqual(credentialsType, Constants.SPACE_CREDENTIALS_USER)) {
        checkContains(params, Constants.SPACE_SERVER_URL, "JetBrains Space server URL", errors);
        checkContains(params, Constants.SPACE_CLIENT_ID, "Client ID", errors);
        checkContains(params, Constants.SPACE_CLIENT_SECRET, "Client secret", errors);
      }

      checkContains(params, Constants.SPACE_PROJECT_KEY, "Project key", errors);
      checkContains(params, Constants.SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME, "Commit status publisher display name", errors);
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

    String projectKey = params.get(Constants.SPACE_PROJECT_KEY);
    if (null == projectKey)
      throw new PublisherException("Missing JetBrains Space project key");

    try {
      SpaceToken token = SpaceToken.requestToken(serviceId, serviceSecret, serverUrl, myGson, trustStore());

      HashMap<String, Object> data = new HashMap<>();
      data.put(PROJECT_KEY_FIELD, SpaceUtils.createProjectKey(projectKey));

      HttpHelper.post(String.format("%s/%s", serverUrl, ENDPOINT_CHECK_SERVICE), null, null, myGson.toJson(data), ContentType.APPLICATION_JSON,
        token.toHeader(), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), new ContentResponseProcessor());
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
}
