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

package jetbrains.buildServer.commitPublisher.github.api.impl;

import java.io.IOException;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApi;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.RepoInfo;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 2:54
 */
public class GitHubApiFactoryImpl implements GitHubApiFactory {
  private final HttpClientWrapper myWrapper;

  @NotNull
  protected final OAuthTokensStorage myOAuthTokensStorage;

  public GitHubApiFactoryImpl(@NotNull final HttpClientWrapper wrapper,
                              @NotNull OAuthTokensStorage oAuthTokensStorage) {
    myWrapper = wrapper;
    myOAuthTokensStorage = oAuthTokensStorage;
  }


  @NotNull
  @Override
  public GitHubApi openGitHubForUser(@NotNull final String url,
                                     @NotNull final String username,
                                     @NotNull final String password) {
    return new GitHubApiImpl(myWrapper, new GitHubApiPaths(url)){
      @Override
      protected SimpleCredentials authenticationCredentials() {
        return new SimpleCredentials(username, password);
      }
    };
  }

  @NotNull
  @Override
  public GitHubApi openGitHubForToken(@NotNull final String url,
                                      @NotNull final String token) {
    return new GitHubApiImpl(myWrapper, new GitHubApiPaths(url)){
      @Override
      protected SimpleCredentials authenticationCredentials() {
        return new SimpleCredentials("x-oauth-basic", token);
      }
    };
  }

  @NotNull
  @Override
  public GitHubApi openGitHubForGitHubApp(@NotNull final String url,
                                          @NotNull final String tokenId,
                                          @NotNull final String vcsRootId) {


    return new GitHubApiImpl(myWrapper, new GitHubApiPaths(url)){
      @Override
      protected SimpleCredentials authenticationCredentials() throws IOException {
        final OAuthToken gitHubAppToken = myOAuthTokensStorage.getRefreshableToken(vcsRootId, tokenId, false);
        if (gitHubAppToken != null) {
          return new SimpleCredentials("oauth2", gitHubAppToken.getAccessToken());
        }
        else {
          throw new IOException("Failed to get installation token for GitHub App connection (tokenId: " + tokenId + ")");
        }
      }

      @Override
      public void testConnection(@NotNull Repository repo) throws PublisherException {
        final String uri = myUrls.getRepoInfo(repo.owner(), repo.repositoryName());

        RepoInfo repoInfo;
        try {
          repoInfo = processResponse(uri, RepoInfo.class, true);
        } catch (Throwable ex) {
          throw new PublisherException(String.format("Error while retrieving \"%s\" repository information", repo.url()), ex);
        }

        if (null == repoInfo.name || null == repoInfo.permissions) {
          throw new PublisherException(String.format("Repository \"%s\" is inaccessible", repo.url()));
        }
      }
    };
  }
}
