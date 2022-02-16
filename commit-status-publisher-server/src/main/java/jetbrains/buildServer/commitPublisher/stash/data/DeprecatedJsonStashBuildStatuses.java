package jetbrains.buildServer.commitPublisher.stash.data;

import java.util.Collection;

public class DeprecatedJsonStashBuildStatuses {
  public boolean isLastPage;
  public int size;
  public Integer nextPageStart;
  public Collection<Status> values;

  public class Status {
    public String description;
    public String key;
    public String url;
    public String name;
    public String state;
  }
}
