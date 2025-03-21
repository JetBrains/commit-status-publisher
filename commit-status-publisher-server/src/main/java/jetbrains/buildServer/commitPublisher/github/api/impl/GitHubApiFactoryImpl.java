

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

package jetbrains.buildServer.commitPublisher.github.api.impl;

import java.io.IOException;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApi;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.RepoInfo;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 2:54
 */
public class GitHubApiFactoryImpl implements GitHubApiFactory {
  private final HttpClientWrapper myWrapper;

  @NotNull
  protected final OAuthTokensStorage myOAuthTokensStorage;

  @NotNull
  protected final OAuthConnectionsManager myConnectionsManager;

  @NotNull
  protected final ProjectManager myProjectManager;

  public GitHubApiFactoryImpl(@NotNull final HttpClientWrapper wrapper,
                              @NotNull OAuthTokensStorage oAuthTokensStorage,
                              @NotNull OAuthConnectionsManager connectionsManager,
                              @NotNull ProjectManager projectManager) {
    myWrapper = wrapper;
    myOAuthTokensStorage = oAuthTokensStorage;
    myConnectionsManager = connectionsManager;
    myProjectManager = projectManager;
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
  public GitHubApi openGitHubForStoredToken(@NotNull final String url,
                                            @NotNull final String tokenId,
                                            @NotNull final SProject project) {

    return new GitHubApiImpl(myWrapper, new GitHubApiPaths(url)){
      @Override
      protected SimpleCredentials authenticationCredentials() throws IOException {
        final OAuthToken gitHubOAuthToken = myOAuthTokensStorage.getToken(project, tokenId, true, true);
        if (gitHubOAuthToken != null) {
          //todo: must be refactored to use Bearer token TW-76091
          return new SimpleCredentials("oauth2", gitHubOAuthToken.getAccessToken());
        }
        else {
          throw new IOException("Failed to get installation token for GitHub App connection (tokenId: " + tokenId + ")");
        }
      }

      @Override
      protected void checkPermissions(@NotNull Repository repo, @NotNull RepoInfo repoInfo) throws PublisherException {
        if (null == repoInfo.name || null == repoInfo.permissions) {
          throw new PublisherException(String.format("Repository \"%s\" is inaccessible", repo.url()));
        }

        final OAuthToken gitHubOAuthToken = myOAuthTokensStorage.getToken(project, tokenId, true, true);
        if (gitHubOAuthToken == null) {
          throw new PublisherException("Failed to retrieve configured token from storage (tokenId: " + tokenId + ")");
        }

        if (SUser.UKNOWN_USER_ID == gitHubOAuthToken.getTeamCityUserId()) { // GitHub App Connection
          final OAuthConnectionDescriptor connection = getConnection(project, tokenId);
          final TokenIntent intent = new TokenIntent(TokenIntentType.PUBLISH_STATUS, repo.url());
          if (!connection.getOauthProvider().isSuitableToken(gitHubOAuthToken, intent)) {
            throw new PublisherException(String.format("The stored token doesn't have push access to the repository \"%s\"", repo.url()));
          }

        } else { // OAuth connection
          if (!repoInfo.permissions.push) {
            throw new PublisherException(String.format("There is no push access to the repository \"%s\"", repo.url()));
          }
        }
      }


      @NotNull
      private OAuthConnectionDescriptor getConnection(@NotNull SProject project, @NotNull String tokenId) throws PublisherException {
        final TokenFullIdComponents components = OAuthTokensStorage.parseFullTokenId(tokenId);
        if (components == null) {
          throw new PublisherException("Unable to parse token id " + tokenId);
        }

        final OAuthConnectionDescriptor connection = myConnectionsManager.findConnectionByTokenStorageId(project, components.getTokenStorageId());
        if (connection == null) {
          throw new PublisherException("Unable to find connection by token id " + tokenId);
        }

        return connection;
      }
    };
  }
}