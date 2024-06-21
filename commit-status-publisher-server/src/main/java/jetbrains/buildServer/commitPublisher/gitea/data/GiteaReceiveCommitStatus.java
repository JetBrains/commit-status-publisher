package jetbrains.buildServer.commitPublisher.gitea.data;

import jetbrains.buildServer.commitPublisher.gitea.GiteaBuildStatus;

public class GiteaReceiveCommitStatus extends GiteaCommitStatus {
  public final String buildName;

  public GiteaReceiveCommitStatus(String context, String description, String state, String target_url, String buildName, String url) {
    super(context, description, state, target_url, url);
    this.buildName = buildName;
  }
}
