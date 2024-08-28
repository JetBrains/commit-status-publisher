

package jetbrains.buildServer.commitPublisher.gitlab.data;

/**
 * This class does not represent full repository information.
 */
public class GitLabRepoInfo {
  public String id;
  public GitLabPermissions permissions;

  public GitLabRepoInfo(String id, GitLabPermissions permissions) {
    this.id = id;
    this.permissions = permissions;
  }

  @Override
  public int hashCode() {
    return id != null ?
      id.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o  == null || getClass() != o.getClass()) return false;
    GitLabRepoInfo repoInfo = (GitLabRepoInfo) o;

    return id != null && repoInfo.id != null && id.equals(repoInfo.id);
  }
}