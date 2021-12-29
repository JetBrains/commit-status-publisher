package jetbrains.buildServer.commitPublisher.gitlab.data;

import java.util.Date;

public class GitLabCommitStatus {
  public long id;
  public String status;
  public Date created_at;
  public String description;
  public String name;
  public String target_url;
}
