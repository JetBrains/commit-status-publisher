package jetbrains.buildServer.commitPublisher.gitlab.data;

public class GitLabReceiveCommitStatus extends GitLabCommitStatus {
  public final Long id;
  public final String status;

  public GitLabReceiveCommitStatus(Long id, String status, String description, String name, String target_url) {
    super(description, name, target_url);
    this.id = id;
    this.status = status;
  }
}
