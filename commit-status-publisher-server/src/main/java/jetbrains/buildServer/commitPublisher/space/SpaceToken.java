package jetbrains.buildServer.commitPublisher.space;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.HttpHelper;
import jetbrains.buildServer.serverSide.IOGuard;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public class SpaceToken {

  private static final String TOKEN_TYPE_FIELD_NAME = "token_type";
  private static final String ACCESS_TOKEN_FIELD_NAME = "access_token";

  private static final String JWT_TOKEN_ENDPOINT = "oauth/token";
  private static final String GRANT_TYPE = "grant_type";
  private static final String CLIENT_CREDENTIALS_GRAND_TYPE = "client_credentials";
  private static final String SCOPE = "scope";
  private static final String ALL_SCOPE = "**";

  private final String myTokenType;
  private final String myAccessToken;

  public SpaceToken(@NotNull String tokenType,
                    @NotNull String accessToken) {
    myTokenType = tokenType;
    myAccessToken = accessToken;
  }

  public String getTokenType() {
    return myTokenType;
  }

  public String getAccessToken() {
    return myAccessToken;
  }

  public Map<String, String> toHeader() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", String.format("%s %s", myTokenType, myAccessToken));
    return headers;
  }

  public static SpaceToken requestToken(@NotNull final String serviceId,
                                        @NotNull final String serviceSecret,
                                        @NotNull final String spaceUrl,
                                        @NotNull Gson gson,
                                        final KeyStore keyStore) throws Exception {

    final String urlPost = HttpHelper.stripTrailingSlash(spaceUrl) + "/" + JWT_TOKEN_ENDPOINT;
    final String data = String.format("%s=%s&%s=%s", GRANT_TYPE, CLIENT_CREDENTIALS_GRAND_TYPE, SCOPE, ALL_SCOPE);
    final ContentResponseProcessor contentResponseProcessor = new ContentResponseProcessor();

    IOGuard.allowNetworkCall(() -> HttpHelper.post(urlPost, serviceId, serviceSecret, data, ContentType.APPLICATION_FORM_URLENCODED, null,
      BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, keyStore, contentResponseProcessor));

    return SpaceToken.parseToken(contentResponseProcessor.getContent(), gson);
  }

  private static SpaceToken parseToken(String token, Gson gson) throws Exception {
    JsonObject jsonObject = gson.fromJson(token, JsonObject.class);
    if (!jsonObject.has(TOKEN_TYPE_FIELD_NAME) || !jsonObject.has(ACCESS_TOKEN_FIELD_NAME)) {
      throw new Exception(String.format("Response body must contains `%s` and `%s` fields. Response body: %s", TOKEN_TYPE_FIELD_NAME, ACCESS_TOKEN_FIELD_NAME, token));
    }
    return new SpaceToken(jsonObject.get(TOKEN_TYPE_FIELD_NAME).getAsString(), jsonObject.get(ACCESS_TOKEN_FIELD_NAME).getAsString());
  }
}