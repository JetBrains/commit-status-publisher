package jetbrains.buildServer.commitPublisher.gitea.data;

public class GiteaCommitStatus {
  public final String context;
  public final String description;
  public final String target_url;

  public GiteaCommitStatus(String context, String description, String target_url) {
    this.context = context;
    this.description = description;
    this.target_url = target_url;
  }
}
