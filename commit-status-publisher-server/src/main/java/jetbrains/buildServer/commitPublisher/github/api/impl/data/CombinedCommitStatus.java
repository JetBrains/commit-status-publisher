package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import java.util.Collection;

public class CombinedCommitStatus {
  public String state;
  public Integer total_count;
  public Collection<CommitStatus> statuses;
}
