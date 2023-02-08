package jetbrains.buildServer.commitPublisher;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
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

  public AuthTypeAwareSettings(@NotNull PluginDescriptor descriptor,
                               @NotNull WebLinks links,
                               @NotNull CommitStatusPublisherProblems problems,
                               @NotNull SSLTrustStoreProvider trustStoreProvider,
                               @NotNull OAuthTokensStorage oAuthTokensStorage,
                               @NotNull UserModel userModel,
                               @NotNull OAuthConnectionsManager oAuthConnectionsManager) {
    super(descriptor, links, problems, trustStoreProvider);
    myOAuthTokensStorage = oAuthTokensStorage;
    myUserModel = userModel;
    myOAuthConnectionsManager = oAuthConnectionsManager;
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
        return new UsernamePasswordCredentials(username, password);

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
        return new BearerTokenCredentials(tokenId, token, root.getExternalId(), myOAuthTokensStorage);

      default:
        throw new PublisherException("unsupported authentication type " + authType);
    }
  }

  @Nullable
  @Override
  public Map<String, Object> checkHealth(@NotNull SBuildType buildType, @NotNull Map<String, String> params) {
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
    final String tokenId = params.get(Constants.TOKEN_ID);
    if (StringUtil.isEmptyOrSpaces(tokenId)) {
      return Collections.emptyMap();
    }

    final OAuthToken token = myOAuthTokensStorage.getRefreshableToken(project, tokenId);
    if (token == null) {
      return Collections.emptyMap();
    }

    final SUser user = myUserModel.findUserById(token.getTeamCityUserId());
    if (user == null) {
      return Collections.emptyMap();
    }

    final TokenFullIdComponents tokenIdComponents = OAuthTokensStorage.parseFullTokenId(tokenId);
    if (tokenIdComponents == null) {
      return Collections.emptyMap();
    }

    final OAuthConnectionDescriptor connection = myOAuthConnectionsManager.findConnectionByTokenStorageId(project, tokenIdComponents.getTokenStorageId());
    if (connection == null) {
      return Collections.emptyMap();
    }

    return ImmutableMap.of(
      "tokenUsername", user.getUsername(),
      "tokenUser", user.getName(),
      "tokenConnection", connection.getConnectionDisplayName()
    );
  }
}
