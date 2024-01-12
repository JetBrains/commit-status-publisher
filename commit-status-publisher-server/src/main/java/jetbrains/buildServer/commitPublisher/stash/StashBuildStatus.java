

package jetbrains.buildServer.commitPublisher.stash;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum StashBuildStatus {
  SUCCESSFUL, FAILED, INPROGRESS;

  private static final Map<String, StashBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(StashBuildStatus::name, Function.identity()));

  @Nullable
  public static StashBuildStatus getByName(@NotNull String name) {
    return INDEX.get(name);
  }
}