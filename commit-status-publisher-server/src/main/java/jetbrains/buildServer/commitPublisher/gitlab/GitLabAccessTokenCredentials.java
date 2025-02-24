/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.AccessTokenCredentials;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitLabAccessTokenCredentials extends AccessTokenCredentials implements HttpCredentials {

  private static final String HEADER_AUTHORIZATION = "Private-Token";

  private static final String OAUTH_HEADER_AUTHORIZATION = "Authorization";
  private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

  public GitLabAccessTokenCredentials(@NotNull final String token) {
    super(token);
  }

  public GitLabAccessTokenCredentials(@NotNull final String tokenId,
                                      @NotNull final OAuthToken refreshableToken,
                                      @NotNull final OAuthTokensStorage tokensStorage,
                                      @NotNull final SProject project) {
    super(tokenId, refreshableToken, tokensStorage, project);
  }

  @Override
  public void set(@NotNull final HTTPRequestBuilder requestBuilder) {
    if (!isRefreshable()) {
      requestBuilder.withHeader(HEADER_AUTHORIZATION, getToken());
    } else {
      requestBuilder.withHeader(OAUTH_HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + getToken());
    }
  }
}
