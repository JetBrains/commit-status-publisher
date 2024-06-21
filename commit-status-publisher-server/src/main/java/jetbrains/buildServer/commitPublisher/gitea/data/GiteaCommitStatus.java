package jetbrains.buildServer.commitPublisher.gitea.data;

import jetbrains.buildServer.commitPublisher.gitea.GiteaBuildStatus;

public class GiteaCommitStatus {
  public final String context;
  public final String description;
  public final String state;
  public final String target_url;
  public final String url;

  public GiteaCommitStatus(String context, String description, String state, String target_url, String url) {
    this.context = context;
    this.description = description;
    this.state = state;
    this.target_url = target_url;
    this.url = url;
  }
}
