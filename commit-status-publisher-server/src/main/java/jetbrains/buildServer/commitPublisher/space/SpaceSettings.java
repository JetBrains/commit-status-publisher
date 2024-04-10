

package jetbrains.buildServer.commitPublisher.space;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.space.data.SpaceBuildStatusInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConstants;
import jetbrains.buildServer.serverSide.oauth.space.SpaceFeatures;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.space.application.SpaceApplicationInformation;
import jetbrains.buildServer.serverSide.oauth.space.application.SpaceApplicationInformationManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.url.InvalidUriException;
import jetbrains.buildServer.vcshostings.url.ServerURI;
import jetbrains.buildServer.vcshostings.url.ServerURIParser;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT;
import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class SpaceSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  @NotNull private final OAuthConnectionsManager myOAuthConnectionManager;
  @NotNull private final SecurityContext mySecurityContext;
  @NotNull private final SpaceApplicationInformationManager myApplicationInformationManager;

  @NotNull private final CommitStatusesCache<SpaceBuildStatusInfo> myStatusesCache;

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

  public SpaceSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems,
                       @NotNull SSLTrustStoreProvider trustStoreProvider,
                       @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                       @NotNull SecurityContext securityContext,
                       @NotNull SpaceApplicationInformationManager applicationInformationManager) {
    super(descriptor, links, problems, trustStoreProvider);
    myOAuthConnectionManager = oAuthConnectionsManager;
    mySecurityContext = securityContext;
    myApplicationInformationManager = applicationInformationManager;

    myStatusesCache = new CommitStatusesCache<>();
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
    return new SpacePublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, connector, myStatusesCache, true);
  }

  @Override
  public boolean isFeatureLessPublishingSupported(@NotNull SBuildType buildType) {
    if (!SpaceFeatures.forScope(buildType).publishCommitStatusUnconditionally()) {
      LOG.debug(() -> "Unconditional commit status publishing is not supported for " + LogUtil.describe(buildType) + ": the feature toggle is disabled");
      return false;
    }

    final List<OAuthConnectionDescriptor> spaceConnections = myOAuthConnectionManager.getAvailableConnectionsOfType(buildType.getProject(), SpaceOAuthProvider.TYPE);
    if (spaceConnections.isEmpty()) {
      LOG.debug(() -> "Unconditional commit status publishing is not supported for " + LogUtil.describe(buildType) + ": there are no Space connections available to the project");
      return false;
    }

    // we don't do a strict check for the publishing capability of the potential connection to prevent unnecessary HTTP requests
    // -> the capability will be checked in #createFeaturelessPublisher later on
    return buildType.getVcsRootInstances().stream()
                    .anyMatch(vcsRootInstance -> findConnectionByVcsRoot(buildType, vcsRootInstance.getParent(), false) != null);
  }

  @Override
  public boolean allowsFeatureLessPublishingForDependencies(@NotNull SBuildType buildType) {
    final SpaceFeatures.UnconditionalPublishingMode mode = SpaceFeatures.forScope(buildType).unconditionalPublishingMode();
    switch (mode) {
      case ALL_BUILDS:
        return true;
      case NO_DEPENDENCIES:
        return false;
      default:
        throw new RuntimeException("unsupported Space unconditional publishing mode " + mode);
    }
  }

  @Override
  public CommitStatusPublisher createFeaturelessPublisher(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot) {
    if (!SpaceFeatures.forScope(buildType).publishCommitStatusUnconditionally()) {
      LOG.debug(() -> "unconditional commit status publishing is not supported for " + LogUtil.describe(buildType) + ": the feature toggle is disabled");
      return null;
    }

    final SpaceConnectDescriber spaceConnection = findConnectionByVcsRoot(buildType, vcsRoot, true);
    if (spaceConnection == null) {
      return null;
    }

    final Map<String, String> params = ImmutableMap.of(
      jetbrains.buildServer.commitPublisher.Constants.VCS_ROOT_ID_PARAM, String.valueOf(vcsRoot.getId())
    );
    final String buildFeatureId = String.format(Constants.SPACE_UNCONDITIONAL_FEATURE_FORMAT, buildType.getInternalId(), vcsRoot.getId());

    return new SpacePublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, spaceConnection, myStatusesCache, false);
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
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
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
    return SpaceFeatures.global().commitStatusEnabled();
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
          url, null, null, ContentType.APPLICATION_JSON,
          headers, DEFAULT_CONNECTION_TIMEOUT, trustStore(), new ContentResponseProcessor()
        )
      );

    } catch (Exception ex) {
      throw new PublisherException("JetBrains Space publisher has failed to test connection", ex);
    }
  }

  @Nullable
  @Override
  public Map<String, Object> checkHealth(@NotNull SBuildType buildType, @NotNull Map<String, String> params) {
    String connectionId = params.get(Constants.SPACE_CONNECTION_ID);
    if (connectionId == null)
      return null;
    OAuthConnectionDescriptor connectionDescriptor = myOAuthConnectionManager.findConnectionById(buildType.getProject(), connectionId);
    if (connectionDescriptor == null) {
      Map<String, Object> healthItemData = new HashMap<>();
      healthItemData.put("message", "refers to a missing JetBrains Space connection id = '" + connectionId + "'");
      return healthItemData;
    }
    return null;
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  @NotNull
  @Override
  public List<OAuthConnectionDescriptor> getOAuthConnections(@NotNull SProject project, @NotNull SUser user) {
    final SpaceFeatures spaceFeatures = SpaceFeatures.forScope(project);
    if (spaceFeatures.capabilitiesEnabled()) {
      // connections will be loaded via JS in this case
      return Collections.emptyList();
    }

    return myOAuthConnectionManager.getAvailableConnectionsOfType(project, SpaceOAuthProvider.TYPE).stream()
      .sorted(CONNECTION_DESCRIPTOR_NAME_COMPARATOR)
      .collect(Collectors.toList());
  }

  @NotNull
  static String getDisplayName(@NotNull Map<String, String> params) {
    String displayName = params.get(Constants.SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME);
    return displayName == null ? Constants.SPACE_DEFAULT_DISPLAY_NAME : displayName;
  }

  @NotNull
  @Override
  public Map<String, Object> getSpecificAttributes(@NotNull SProject project, @NotNull Map<String, String> params) {
    return ImmutableMap.of(
      "canEditProject", AuthUtil.hasPermissionToManageProject(mySecurityContext.getAuthorityHolder(), project.getProjectId()),
      "spaceFeatures", SpaceFeatures.forScope(project),
      "rightsInfo", SpaceConstants.RIGHTS_INFO_COMMIT_STATUS
    );
  }

  @Nullable
  private SpaceConnectDescriber findConnectionByVcsRoot(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot, boolean checkPublishingCapability) {
    final String tokenId = vcsRoot.getProperty("tokenId");
    if (tokenId == null) {
      debugLogUnconditionalPublishingInfo(buildType,
                                          () -> "VCS root " + LogUtil.describe(vcsRoot) + " is not using refreshable tokens, trying to guess Space connection from fetch URL");
      return guessConnectionFromFetchUrl(buildType, vcsRoot, checkPublishingCapability);
    }

    return findConnectionByTokenId(buildType, vcsRoot, tokenId, checkPublishingCapability);
  }

  @Nullable
  private SpaceConnectDescriber findConnectionByTokenId(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot, @NotNull String tokenId, boolean checkPublishingCapability) {
    final TokenFullIdComponents tokenFullIdComponents = OAuthTokensStorage.parseFullTokenId(tokenId);
    if (tokenFullIdComponents == null) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "VCS root " + LogUtil.describe(vcsRoot) + " can't be used: unparseable token ID " + tokenId);
      return null;
    }

    final OAuthConnectionDescriptor connection = myOAuthConnectionManager.findConnectionByTokenStorageId(buildType.getProject(), tokenFullIdComponents.getTokenStorageId());
    if (!SpaceOAuthProvider.TYPE.equals(connection.getOauthProvider().getType())) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "Connection " + LogUtil.describe(connection) + " can't be used: not a Space connection.");
      return null;
    }

    if (checkPublishingCapability && !connection.hasCapability(ConnectionCapability.PUBLISH_BUILD_STATUS)) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "Space connection " + LogUtil.describe(connection) + " can't be used: missing capability.");
      return null;
    }

    debugLogUnconditionalPublishingInfo(buildType, () -> "VCS root " + LogUtil.describe(vcsRoot) + " will be used: it is using Space refreshable tokens.");
    return new SpaceConnectDescriber(connection);
  }

  @Nullable
  private SpaceConnectDescriber guessConnectionFromFetchUrl(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot, boolean checkPublishingCapability) {
    final String fetchUrl = vcsRoot.getProperty("url");
    if (StringUtil.isEmptyOrSpaces(fetchUrl)) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "VCS root " + LogUtil.describe(vcsRoot) + " can't be used: the fetch URL is empty");
      return null;
    }

    final TokenizedFetchUrl tokenizedFetchUrl;
    try {
      tokenizedFetchUrl = tokenizeFetchUrl(fetchUrl);
    } catch (InvalidUriException e) {
      debugLogUnconditionalPublishingInfo(buildType,
                                          () -> "VCS root " + LogUtil.describe(vcsRoot) + " can't be used: failed to extract project key out of fetch URL " + fetchUrl,
                                          e);
      return null;
    }

    if (tokenizedFetchUrl.getProjectKey() == null) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "VCS root " + LogUtil.describe(vcsRoot) + " can't be used: unable to locate project key in fetch URL " + fetchUrl);
      return null;
    }
    final String projectKey = tokenizedFetchUrl.getProjectKey().toUpperCase(Locale.ROOT);

    final List<OAuthConnectionDescriptor> potentialConnections = myOAuthConnectionManager.getAvailableConnectionsOfType(buildType.getProject(), SpaceOAuthProvider.TYPE);
    for (OAuthConnectionDescriptor potentialConnection : potentialConnections) {
      final SpaceConnectDescriber spaceConnection = new SpaceConnectDescriber(potentialConnection);
      if (!matchesUrl(buildType, potentialConnection, spaceConnection, tokenizedFetchUrl)) {
        continue;
      }

      if (!checkPublishingCapability) {
        return spaceConnection;
      }

      final SpaceApplicationInformation applicationInfo = myApplicationInformationManager.getForConnection(spaceConnection);
      if (hasPublishStatusRights(buildType, potentialConnection, applicationInfo, projectKey)) {
        return spaceConnection;
      }
    }

    debugLogUnconditionalPublishingInfo(buildType, () -> "VCS root " + LogUtil.describe(vcsRoot) + " can't be used: there is no usable connection for project key " + projectKey);
    return null;
  }

  private boolean hasPublishStatusRights(@NotNull SBuildType buildType,
                                         @NotNull OAuthConnectionDescriptor connection,
                                         @NotNull SpaceApplicationInformation applicationInformation,
                                         @NotNull String projectKey) {
    final String contextIdentifierProject = SpaceConstants.CONTEXT_IDENTIFIER_PROJECT_KEY + projectKey;
    if (applicationInformation.getContextRights(contextIdentifierProject).areGranted(SpaceConstants.RIGHTS_COMMIT_STATUS)) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "Space connection " + LogUtil.describe(connection) +
                                                           " will be used: sufficient rights to publish statuses are granted for the project " + projectKey);
      return true;
    }

    if (applicationInformation.getGlobalRights().areGranted(SpaceConstants.RIGHTS_COMMIT_STATUS)) {
      debugLogUnconditionalPublishingInfo(buildType,
                                          () -> "Space connection " + LogUtil.describe(connection) + " will be used: sufficient rights to publish statuses are granted globally.");
      return true;
    }

    debugLogUnconditionalPublishingInfo(buildType, () -> "Space connection " + LogUtil.describe(connection) + " can't be used: insufficient rights to publish statuses.");
    return false;
  }

  private boolean matchesUrl(@NotNull SBuildType buildType,
                             @NotNull OAuthConnectionDescriptor connection,
                             @NotNull SpaceConnectDescriber spaceConnection,
                             @NotNull TokenizedFetchUrl tokenizedFetchUrl) {
    final TokenizedServiceUrl tokenizedServiceUrl;
    try {
      tokenizedServiceUrl = tokenizeServiceUrl(spaceConnection.getFullAddress());
    } catch (IllegalArgumentException e) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "Space connection " + LogUtil.describe(connection) + " can't be used: service URL is not parseable", e);
      return false;
    }

    if (!Objects.equals(tokenizedServiceUrl.getOrganization(), tokenizedFetchUrl.getOrganization())) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "Space connection " + LogUtil.describe(connection) + " can't be used: organizations don't match, " +
                                                           tokenizedServiceUrl.getOrganization() + " (from service URL) != " + tokenizedFetchUrl.getOrganization() +
                                                           " (from fetch URL).");
      return false;
    }

    if (tokenizedServiceUrl.getHost().equals(tokenizedServiceUrl.getHost())) {
      debugLogUnconditionalPublishingInfo(buildType,
                                          () -> "Space connection " + LogUtil.describe(connection) + " will be used: fetch URL and service URL indicate identical host.");
      return true;
    }

    final String[] serviceHostTokens = tokenizedServiceUrl.getHost().split("\\.");
    final String[] fetchHostTokens = tokenizedFetchUrl.getHost().split("\\.");
    int matchCount = 0;
    int i = serviceHostTokens.length - 1;
    int j = fetchHostTokens.length - 1;
    while (i >= 0 && j >= 0 && serviceHostTokens[i--].equals(fetchHostTokens[j--])) {
      matchCount++;
    }

    if (matchCount <= 1) {
      debugLogUnconditionalPublishingInfo(buildType, () -> "Space connection " + LogUtil.describe(connection) + " can't be used: hosts aren't similar enough, " +
                                                           tokenizedServiceUrl.getHost() + "(from service URL) vs " + tokenizedFetchUrl.getHost() + " (from fetch URL).");
      return false;
    }

    debugLogUnconditionalPublishingInfo(buildType,
                                        () -> "Space connection " + LogUtil.describe(connection) + " will be used: hosts are similar enough, " + tokenizedServiceUrl.getHost() +
                                              "(from service URL) vs " + tokenizedFetchUrl.getHost() + " (from fetch URL).");
    return true;
  }

  @NotNull
  private static TokenizedFetchUrl tokenizeFetchUrl(@NotNull String fetchUrl) {
    final ServerURI serverURI = ServerURIParser.createServerURI(fetchUrl.toLowerCase(Locale.ROOT));
    final String host = serverURI.getHost();

    //                                        size - 3 |  size - 2 |  size - 1
    // cloud:   ssh://git@git.jetbrains.space/test-org/test-project/testrepo.git
    // on-prem: ssh://git@git.mycompany.test/test-project/testrepo.git
    final List<String> pathFragments = serverURI.getPathFragments();
    int size = pathFragments.size();
    if (size < 2) {
      return new TokenizedFetchUrl(host, null, null);
    }
    final String projectKey = pathFragments.get(size - 2);
    final String organization = (host.endsWith(Constants.DOMAIN_SPACE_CLOUD) && size >= 3) ? pathFragments.get(size - 3) : null;

    return new TokenizedFetchUrl(host, organization, projectKey);
  }

  @NotNull
  private static TokenizedServiceUrl tokenizeServiceUrl(@NotNull String serviceUrl) {
    final URI uri = URI.create(serviceUrl);
    final String host = uri.getHost();
    final String organization = host.endsWith(Constants.DOMAIN_SPACE_CLOUD) ? host.split("\\.")[0] : null;
    return new TokenizedServiceUrl(host, organization);
  }

  private void debugLogUnconditionalPublishingInfo(@NotNull SBuildType buildType, @NotNull Supplier<String> messageSupplier) {
    LOG.debug(chainLogMessageSupplier(buildType, messageSupplier));
  }

  private void debugLogUnconditionalPublishingInfo(@NotNull SBuildType buildType, @NotNull Supplier<String> messageSupplier, @NotNull Throwable t) {
    LOG.debug(chainLogMessageSupplier(buildType, messageSupplier), t);
  }

  private static Logger.MessageSupplier chainLogMessageSupplier(@NotNull SBuildType buildType, @NotNull Supplier<String> messageSupplier) {
    return () -> "Unconditional commit status publishing for " + LogUtil.describe(buildType) + ": " + messageSupplier.get();
  }

  private static class TokenizedFetchUrl {
    @NotNull private final String myHost;
    @Nullable private final String myOrganization;
    @Nullable private final String myProjectKey;

    public TokenizedFetchUrl(@NotNull String host, @Nullable String organization, @Nullable String projectKey) {
      myHost = host;
      myOrganization = organization;
      myProjectKey = projectKey;
    }

    @NotNull
    public String getHost() {
      return myHost;
    }

    @Nullable
    public String getOrganization() {
      return myOrganization;
    }

    @Nullable
    public String getProjectKey() {
      return myProjectKey;
    }
  }

  private static class TokenizedServiceUrl {
    @NotNull private final String myHost;
    @Nullable private final String myOrganization;

    public TokenizedServiceUrl(@NotNull String host, @Nullable String organization) {
      myHost = host;
      myOrganization = organization;
    }

    @NotNull
    public String getHost() {
      return myHost;
    }

    @Nullable
    public String getOrganization() {
      return myOrganization;
    }
  }
}