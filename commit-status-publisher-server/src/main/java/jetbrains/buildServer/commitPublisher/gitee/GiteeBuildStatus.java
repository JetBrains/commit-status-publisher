

package jetbrains.buildServer.commitPublisher.gitee;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum GiteeBuildStatus {
  PENDING("pending"),
  SUCCESS("success"),
  ERROR("error"),
  FAILURE("failure"),
  QUEUED("queued"),
  INTERRUPTED("interruped");


  private static final Map<String, GiteeBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(val -> val.name(), Function.identity()));


  private final String myName;

  GiteeBuildStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public static GiteeBuildStatus getByName(String name) {
    return INDEX.get(name);
  }

}