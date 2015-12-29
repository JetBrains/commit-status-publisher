package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

public class Repository extends Pair<String, String> {
  public Repository(@NotNull String owner, @NotNull String repo) {
    super(owner, repo);
  }

  @NotNull
  public String owner() {
    return first;
  }

  @NotNull
  public String repositoryName() {
    return second;
  }
}
