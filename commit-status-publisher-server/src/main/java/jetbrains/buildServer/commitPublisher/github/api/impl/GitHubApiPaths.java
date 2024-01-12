

package jetbrains.buildServer.commitPublisher.github.api.impl;

import jetbrains.buildServer.vcshostings.http.HttpHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 19.04.13 19:17
 */
public class GitHubApiPaths {
  private final String myUrl;

  public GitHubApiPaths(@NotNull String url) {
    myUrl = HttpHelper.stripTrailingSlash(url);
  }

  @NotNull
  public String getRepoInfo(@NotNull final String repoOwner,
                            @NotNull final String repoName) {
    // /repos/:owner/:repo
    return myUrl + "/repos/" + repoOwner + "/" + repoName;
  }

  @NotNull
  public String getCommitInfo(@NotNull final String repoOwner,
                              @NotNull final String repoName,
                              @NotNull final String hash) {
    // /repos/:owner/:repo/git/commits/:sha
    return myUrl + "/repos/" + repoOwner + "/" + repoName + "/git/commits/" + hash;
  }

  @NotNull
  public String getCombinedStatusUrl(@NotNull final String ownerName,
                                     @NotNull final String repoName,
                                     @NotNull final String hash,
                                     @Nullable final Integer perPage,
                                     @Nullable final Integer page) {
    StringBuilder url = new StringBuilder(String.format("%s/repos/%s/%s/commits/%s/status", myUrl, ownerName, repoName, hash));
    boolean paramAdded = false;
    if (perPage != null) {
      url.append("?per_page=").append(perPage);
      paramAdded = true;
    }
    if (page != null) {
      url.append(paramAdded ? "&" : "?").append("page=").append(page);
    }
    return url.toString();
  }

  @NotNull
  public String getStatusUrl(@NotNull final String ownerName,
                             @NotNull final String repoName,
                             @NotNull final String hash) {
    return myUrl + "/repos/" + ownerName + "/" + repoName + "/statuses/" + hash;
  }

  @NotNull
  public String getPullRequestInfo(@NotNull final String repoOwner,
                                   @NotNull final String repoName,
                                   @NotNull final String pullRequestId) {
    return myUrl + "/repos/" + repoOwner + "/" + repoName + "/pulls/" + pullRequestId;
  }

  @NotNull
  public String getAddCommentUrl(@NotNull final String ownerName,
                                 @NotNull final String repoName,
                                 @NotNull final String hash) {
    ///repos/:owner/:repo/commits/:sha/comments
    return myUrl + "/repos/" + ownerName + "/" + repoName + "/commits/" + hash + "/comments";
  }
}