

package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 04.03.13 22:32
 */
public class RepoInfo {
  @Nullable
  public Long id;

  @Nullable
  public String name;

  @Nullable
  public Permissions permissions;
}