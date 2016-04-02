/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiPaths;

/**
 * Created by github.com/justmara on 15.03.2016.
 */
public class GitLabApiPaths extends GitHubApiPaths {

  public GitLabApiPaths(@NotNull String url) {
    super(url);
  }

  @NotNull
  public String getCommitInfo(@NotNull final String repoOwner,
                              @NotNull final String repoName,
                              @NotNull final String hash) {
    return myUrl + "/projects/" + repoOwner + "%2F" + repoName + "/repository/commits/" + hash;
  }

  @NotNull
  public String getStatusUrl(@NotNull final String ownerName,
                             @NotNull final String repoName,
                             @NotNull final String hash) {
    return myUrl + "/projects/" + ownerName + "%2F" + repoName + "/repository/commits/" + hash + "/statuses";
  }

  @NotNull
  public String setStatusUrl(@NotNull final String ownerName,
                             @NotNull final String repoName,
                             @NotNull final String hash) {
    return myUrl + "/projects/" + ownerName + "%2F" + repoName + "/statuses/" + hash;
  }

  @NotNull
  public String getPullRequestInfo(@NotNull final String repoOwner,
                                   @NotNull final String repoName,
                                   @NotNull final String pullRequestId) {
    return myUrl + "/projects/" + repoOwner + "%2F" + repoName + "/merge_requests/" + pullRequestId;
  }

  @NotNull
  public String getAddCommentUrl(@NotNull final String ownerName,
                                 @NotNull final String repoName,
                                 @NotNull final String hash) {
    return myUrl + "/projects/" + ownerName + "%2F" + repoName + "/repository/commits/" + hash + "/comments";
  }
}
