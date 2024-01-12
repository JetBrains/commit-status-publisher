

package jetbrains.buildServer.commitPublisher.space;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * CommitExecutionStatus
 */
public enum SpaceBuildStatus {
  SCHEDULED("SCHEDULED"),
  RUNNING("RUNNING"),
  FAILING("FAILING"),
  SUCCEEDED("SUCCEEDED"),
  FAILED("FAILED"),
  TERMINATED("TERMINATED");

  private static final Map<String, SpaceBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(SpaceBuildStatus::getName, Function.identity()));

  private final String myName;

  SpaceBuildStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public static SpaceBuildStatus getByName(String name) {
    return INDEX.get(name);
  }
}