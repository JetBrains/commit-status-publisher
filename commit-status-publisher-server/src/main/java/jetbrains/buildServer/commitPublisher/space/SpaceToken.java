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

package jetbrains.buildServer.commitPublisher.space;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

public class SpaceToken {

  static final String TOKEN_TYPE_FIELD_NAME = "token_type";
  static final String ACCESS_TOKEN_FIELD_NAME = "access_token";

  static final String JWT_TOKEN_ENDPOINT = "oauth/token";
  static final String GRANT_TYPE = "grant_type";
  static final String CLIENT_CREDENTIALS_GRAND_TYPE = "client_credentials";
  static final String SCOPE = "scope";
  static final String ALL_SCOPE = "**";

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
    toHeader(headers);
    return headers;
  }

  public void toHeader(Map<String, String> headers) {
    headers.put("Authorization", String.format("%s %s", myTokenType, myAccessToken));
  }

  public static SpaceToken requestToken(@NotNull final String serviceId,
                                        @NotNull final String serviceSecret,
                                        @NotNull final String spaceUrl,
                                        int connectionTimeout, @NotNull Gson gson,
                                        final KeyStore keyStore) throws Exception {

    final String urlPost = HttpHelper.stripTrailingSlash(spaceUrl) + "/" + JWT_TOKEN_ENDPOINT;
    final String data = String.format("%s=%s&%s=%s", GRANT_TYPE, CLIENT_CREDENTIALS_GRAND_TYPE, SCOPE, ALL_SCOPE);
    final ContentResponseProcessor contentResponseProcessor = new ContentResponseProcessor();

    IOGuard.allowNetworkCall(() ->
      HttpHelper.post(
        urlPost, new UsernamePasswordCredentials(serviceId, serviceSecret), data, ContentType.APPLICATION_FORM_URLENCODED,
        Collections.singletonMap(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()),
        connectionTimeout, keyStore, contentResponseProcessor
      )
    );

    return SpaceToken.parseToken(contentResponseProcessor.getContent(), gson);
  }

  private static SpaceToken parseToken(@NotNull String token, @NotNull Gson gson) throws Exception {
    JsonObject jsonObject = gson.fromJson(token, JsonObject.class);
    if (jsonObject == null || !jsonObject.has(TOKEN_TYPE_FIELD_NAME) || !jsonObject.has(ACCESS_TOKEN_FIELD_NAME)) {
      throw new Exception(String.format("Response body must contains `%s` and `%s` fields. Response body: %s", TOKEN_TYPE_FIELD_NAME, ACCESS_TOKEN_FIELD_NAME, token));
    }
    return new SpaceToken(jsonObject.get(TOKEN_TYPE_FIELD_NAME).getAsString(), jsonObject.get(ACCESS_TOKEN_FIELD_NAME).getAsString());
  }
}
