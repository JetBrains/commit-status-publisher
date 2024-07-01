

package jetbrains.buildServer.commitPublisher.gitea.data;


/**
 * This class does not represent full repository information.
 */
public class GiteaRepoInfo {
  public String full_name;
  public String description;
  public GiteaRepoPermissions permissions;
}