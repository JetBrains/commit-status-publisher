

package jetbrains.buildServer.commitPublisher.gitlab;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum GitlabBuildStatus {
  PENDING("pending"),
  RUNNING("running"),
  SUCCESS("success"),
  FAILED("failed"),
  CANCELED("canceled");

  private static final Map<String, GitlabBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(GitlabBuildStatus::getName, Function.identity()));

  private final String myName;

  GitlabBuildStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public static GitlabBuildStatus getByName(@NotNull String name) {
    return INDEX.get(name);
  }
}