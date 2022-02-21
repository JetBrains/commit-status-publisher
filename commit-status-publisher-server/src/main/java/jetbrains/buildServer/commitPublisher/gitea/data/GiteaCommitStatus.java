package jetbrains.buildServer.commitPublisher.gitea.data;

/**
 * @author Felix Heim, 21/02/22.
 */
public class GiteaCommitStatus {
  public final Long id;
  public final String status;
  public final String description;
  public final String name;
  public final String target_url;

  public GiteaCommitStatus(Long id, String status, String description, String name, String target_url) {
    this.id = id;
    this.status = status;
    this.description = description;
    this.name = name;
    this.target_url = target_url;
  }
}
