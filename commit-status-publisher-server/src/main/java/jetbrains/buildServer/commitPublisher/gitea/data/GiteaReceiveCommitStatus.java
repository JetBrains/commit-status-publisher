package jetbrains.buildServer.commitPublisher.gitea.data;

public class GiteaReceiveCommitStatus extends GiteaCommitStatus {
  public final int id;
  public final String status;

  public GiteaReceiveCommitStatus(String context, String description, String target_url, String status, int id) {
    super(context, description, target_url);
    this.id = id;
    this.status = status; // called state when posting and status when receiving from api
  }
}
