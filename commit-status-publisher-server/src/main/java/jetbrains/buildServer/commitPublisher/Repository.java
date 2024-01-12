

package jetbrains.buildServer.commitPublisher;

import org.jetbrains.annotations.NotNull;

public class Repository {
  private final String myOwner, myRepo, myUrl;

  public Repository(@NotNull String url, @NotNull String owner, @NotNull String repo) {
    myUrl = url;
    myOwner = owner;
    myRepo = repo;
  }

  @NotNull
  public String url() {return myUrl; }

  @NotNull
  public String owner() {
    return myOwner;
  }

  @NotNull
  public String repositoryName() {
    return myRepo;
  }
}