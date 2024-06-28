package jetbrains.buildServer.commitPublisher.gitea.data;

public class GiteaPublishCommitStatus extends GiteaCommitStatus {
  public final String state;

  public GiteaPublishCommitStatus(String context, String description, String state, String target_url) {
    super(context, description, target_url);
    this.state = state;
  }
}
