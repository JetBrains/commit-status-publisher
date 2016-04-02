/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.gitlab.api.impl;

import jetbrains.buildServer.commitPublisher.github.api.GitHubApi;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.commitPublisher.gitlab.api.GitLabApiFactory;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapper;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by github.com/justmara on 15.03.2016.
 */
public class GitLabApiFactoryImpl extends GitHubApiFactoryImpl implements GitLabApiFactory {

  public GitLabApiFactoryImpl(@NotNull final HttpClientWrapper wrapper) {
    super(wrapper);
  }

  @NotNull
  public GitHubApi openWithToken(@NotNull final String url,
                                 @NotNull final String token) {
    return new GitLabApiImpl(myWrapper, new GitLabApiPaths(url)){
      @Override
      protected void setAuthentication(@NotNull HttpRequest request) throws AuthenticationException {
        request.addHeader("PRIVATE-TOKEN", token);
      }
    };
  }
}
