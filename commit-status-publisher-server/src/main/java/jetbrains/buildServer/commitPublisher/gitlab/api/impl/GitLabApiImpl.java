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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.github.api.impl.*;
import jetbrains.buildServer.commitPublisher.gitlab.api.GitLabApi;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 * @author Tomaz Cerar
 *         Date: 05.09.12 23:39
 */
public abstract class GitLabApiImpl extends GitHubApiImpl implements GitLabApi {
  private static final Logger LOG = Logger.getInstance(GitLabApiImpl.class.getName());
  private static final Pattern PULL_REQUEST_BRANCH = Pattern.compile("/?refs/merge-requests/(\\d+)/head");

  public GitLabApiImpl(@NotNull final HttpClientWrapper client,
                       @NotNull final GitLabApiPaths urls
  ) {
    super(client, urls);
  }

  @Nullable
  protected String getPullRequestId(@NotNull String repoName,
                                         @NotNull String branchName) {
    final Matcher matcher = PULL_REQUEST_BRANCH.matcher(branchName);
    if (!matcher.matches()) {
      LOG.debug("Branch " + branchName + " for repo " + repoName + " does not look like pull request");
      return null;
    }

    final String pullRequestId = matcher.group(1);
    if (pullRequestId == null) {
      LOG.debug("Branch " + branchName + " for repo " + repoName + " does not contain pull request id");
      return null;
    }
    return pullRequestId;
  }

  public boolean isPullRequestMergeBranch(@NotNull String branchName) {
    final Matcher match = PULL_REQUEST_BRANCH.matcher(branchName);
    return match.matches();// && "merge".equals(match.group(2));
  }
}
