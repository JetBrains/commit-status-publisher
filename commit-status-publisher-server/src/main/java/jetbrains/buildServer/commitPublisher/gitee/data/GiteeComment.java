package jetbrains.buildServer.commitPublisher.gitee.data;

public class GiteeComment {
  public final String context;
  public final String status;
  public final String target_url;

  public GiteeComment(String context, String description, String status, String target_url) {
    this.context = context;
    this.status = status;
    this.target_url = target_url;
  }
}
