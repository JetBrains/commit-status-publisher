

package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 04.03.13 22:33
 */
public class PullRequestInfo {
  @Nullable
  public RepoRefInfo head;
  @Nullable
  public RepoRefInfo base;
}