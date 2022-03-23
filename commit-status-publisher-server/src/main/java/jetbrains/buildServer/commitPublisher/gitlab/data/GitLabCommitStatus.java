package jetbrains.buildServer.commitPublisher.gitlab.data;

public abstract class GitLabCommitStatus {
  public final String name;
  public final String description;
  public final String target_url;

  GitLabCommitStatus(String description, String name, String target_url) {
    this.name = name;
    this.description = description;
    this.target_url = target_url;
  }
}
