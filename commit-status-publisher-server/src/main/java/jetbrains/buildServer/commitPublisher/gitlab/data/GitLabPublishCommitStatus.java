package jetbrains.buildServer.commitPublisher.gitlab.data;

public class GitLabPublishCommitStatus extends GitLabCommitStatus {
  public final String state;
  public final String ref;

  public GitLabPublishCommitStatus(String state, String description, String name, String target_url, String ref) {
    super(description, name, target_url);
    this.state = state;
    this.ref = ref;
  }
}
