/**
 * This class does not represent full repository information.
 */

package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 04.03.13 22:32
 */
public class RepoRefInfo {
  @Nullable
  public String label;

  @Nullable
  public String ref;

  @Nullable
  public String sha;
}
