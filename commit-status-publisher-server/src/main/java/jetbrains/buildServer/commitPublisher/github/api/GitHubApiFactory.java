

package jetbrains.buildServer.commitPublisher.github.api;

import org.jetbrains.annotations.NotNull;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 2:54
 */
public interface GitHubApiFactory {
  public static final String DEFAULT_URL = "https://api.github.com";

  @NotNull
  GitHubApi openGitHubForUser(@NotNull String url,
                              @NotNull String username,
                              @NotNull String password);

  @NotNull
  GitHubApi openGitHubForToken(@NotNull String url,
                               @NotNull String token);

  @NotNull
  GitHubApi openGitHubForStoredToken(@NotNull String url,
                                     @NotNull String tokenId,
                                     @NotNull String vcsRootId);
}