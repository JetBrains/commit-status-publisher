package jetbrains.buildServer.commitPublisher;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.pullRequests.VcsAuthType;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.BearerTokenCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AuthTypeAwareSettings extends BasePublisherSettings {

  @NotNull
  protected final OAuthTokensStorage myOAuthTokensStorage;

  @NotNull
  protected final UserModel myUserModel;

  @NotNull
  protected final OAuthConnectionsManager myOAuthConnectionsManager;

  @NotNull
  private final SecurityContext mySecurityContext;

  public AuthTypeAwareSettings(@NotNull PluginDescriptor descriptor,
                               @NotNull WebLinks links,
                               @NotNull CommitStatusPublisherProblems problems,
                               @NotNull SSLTrustStoreProvider trustStoreProvider,
                               @NotNull OAuthTokensStorage oAuthTokensStorage,
                               @NotNull UserModel userModel,
                               @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                               @NotNull SecurityContext securityContext) {
    super(descriptor, links, problems, trustStoreProvider);
    myOAuthTokensStorage = oAuthTokensStorage;
    myUserModel = userModel;
    myOAuthConnectionsManager = oAuthConnectionsManager;
    mySecurityContext = securityContext;
  }

  @NotNull
  protected abstract String getDefaultAuthType();

  @Nullable
  protected abstract String getUsername(@NotNull Map<String, String> params);

  @Nullable
  protected abstract String getPassword(@NotNull Map<String, String> params);

  @NotNull
  protected String getAuthType(@NotNull Map<String, String> params) {
    return params.getOrDefault(Constants.AUTH_TYPE, getDefaultAuthType());
  }

  @NotNull
  @Override
  public HttpCredentials getCredentials(@Nullable VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    final HttpCredentials credentials = super.getCredentials(root, params);
    if (credentials != null) {
      return credentials;
    }

    final String authType = getAuthType(params);
    switch (authType) {
      case Constants.PASSWORD:
        final String username = getUsername(params);
        if (StringUtil.isEmptyOrSpaces(username)) {
          throw new PublisherException("authentication type is set to password, but username is not configured");
        }
        final String password = getPassword(params);
        if (StringUtil.isEmptyOrSpaces(password)) {
          throw new PublisherException("authentication type is set to password, but password is not configured");
        }
        return getUsernamePasswordCredentials(username, password);

      case Constants.AUTH_TYPE_STORED_TOKEN:
        if (root == null) {
          throw new PublisherException("unable to determine VCS root, authentication via access token is not possible");
        }
        final String tokenId = params.get(Constants.TOKEN_ID);
        if (StringUtil.isEmptyOrSpaces(tokenId)) {
          throw new PublisherException("authentication type is set to access token, but no token id is configured");
        }
        final OAuthToken token = myOAuthTokensStorage.getRefreshableToken(root.getExternalId(), tokenId);
        if (token == null) {
          throw new PublisherException("The configured authentication with the " + tokenId + " ID is missing or invalid.");
        }
        return getStoredTokenCredentials(tokenId, token, root);

      case Constants.AUTH_TYPE_VCS:
        return getVcsRootCredentials(root);

      case Constants.AUTH_TYPE_ACCESS_TOKEN:
        return getAccessTokenCredentials(params);

      default:
        throw new PublisherException("unsupported authentication type " + authType);
    }
  }

  @NotNull
  protected HttpCredentials getUsernamePasswordCredentials(@NotNull final String username, @NotNull final String password) throws PublisherException {
    return new UsernamePasswordCredentials(username, password);
  }

  @NotNull
  protected HttpCredentials getStoredTokenCredentials(@NotNull final String tokenId, @NotNull final OAuthToken token, @NotNull final VcsRoot root) throws PublisherException {
    return new BearerTokenCredentials(tokenId, token, root.getExternalId(), myOAuthTokensStorage);
  }

  @NotNull
  protected HttpCredentials getAccessTokenCredentials(@NotNull final String token) throws PublisherException {
    return new BearerTokenCredentials(token);
  }

  @NotNull
  protected HttpCredentials getAccessTokenCredentials(@NotNull final Map<String, String> params) throws PublisherException {
    throw new PublisherException("Access token authentication type is not supported");
  }

  @NotNull
  protected HttpCredentials getVcsRootCredentials(@Nullable VcsRoot root) throws PublisherException {
    if (root == null) {
      throw new PublisherException("unable to determine VCS root to using credentials");
    }

    Map<String, String> vcsProperties = root.getProperties();
    VcsAuthType vcsAuthType = VcsAuthType.get(vcsProperties);

    if (vcsAuthType.equals(VcsAuthType.PASSWORD)) {
      return getVcsRootPasswordCredentials(root, vcsProperties);
    } else if (vcsAuthType.equals(VcsAuthType.ACCESS_TOKEN)) { // refreshable token
      return getVcsRootRefreshableTokenCredentials(root, vcsProperties);
    } else {
      throw new PublisherException("unsupported VCS authentication type " + vcsAuthType);
    }
  }

  public HttpCredentials getVcsRootPasswordCredentials(@NotNull VcsRoot root, @Nullable Map<String, String> vcsProperties) throws PublisherException {
    if (vcsProperties == null) vcsProperties = root.getProperties();

    final String username = vcsProperties.get("username");
    final String password = vcsProperties.get("secure:password");
    if (!StringUtil.isEmpty(username)) {
      if (HttpHelper.X_OAUTH_BASIC.equals(password)) {
        return getAccessTokenCredentials(username);
      } else {
        return getUsernamePasswordCredentials(username, password == null ? "" : password);
      }
    }
    else if (!StringUtil.isEmpty(password)) {
      return getAccessTokenCredentials(password);
    } else {
      throw new PublisherException("unable to get username/password credentials from VCS Root " + root.getVcsName());
    }
  }

  public HttpCredentials getVcsRootRefreshableTokenCredentials(@NotNull VcsRoot root, @Nullable Map<String, String> vcsProperties) throws PublisherException {
    if (vcsProperties == null) vcsProperties = root.getProperties();

    final String tokenId = vcsProperties.get("tokenId");
    if (StringUtil.isEmpty(tokenId)) {
      throw new PublisherException("unable to get refreshable credentials from VCS Root " + root.getVcsName());
    }

    OAuthToken token = myOAuthTokensStorage.getRefreshableToken(root.getExternalId(), tokenId);
    if (token == null) {
      throw new PublisherException("The configured VCS Root (" + root.getVcsName() + ") authentication with the refreshable token is missing or invalid.");
    }

    return getStoredTokenCredentials(tokenId, token, root);
  }

  @Nullable
  @Override
  public Map<String, Object> checkHealth(@NotNull SBuildType buildType, @NotNull Map<String, String> params) {
    if (Constants.AUTH_TYPE_STORED_TOKEN.equals(getAuthType(params))) {
      final String tokenId = params.get(Constants.TOKEN_ID);
      if (StringUtil.isEmptyOrSpaces(tokenId)) {
        return healthItemData("has authentication type set to access token, but no token id is configured");
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

  @NotNull
  @Override
  public Map<String, Object> getSpecificAttributes(@NotNull SProject project, @NotNull Map<String, String> params) {
    Map<String, Object> result = new HashMap<>();
    final boolean canEditProject = AuthUtil.hasPermissionToManageProject(mySecurityContext.getAuthorityHolder(), project.getProjectId());

    result.put("canEditProject", canEditProject);

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

    final OAuthConnectionDescriptor connection = myOAuthConnectionsManager.findConnectionByTokenStorageId(project, tokenIdComponents.getTokenStorageId());
    if (connection == null) {
      return result;
    }

    result.put("tokenConnection", connection.getConnectionDisplayName());

    final SUser user = myUserModel.findUserById(token.getTeamCityUserId());
    if (user == null) {
      return result;
    }

    result.put("tokenUsername", user.getUsername());
    result.put("tokenUser", user.getName());

    return result;
  }
}
