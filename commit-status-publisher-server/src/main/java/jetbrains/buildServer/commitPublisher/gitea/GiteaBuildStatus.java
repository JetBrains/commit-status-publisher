

package jetbrains.buildServer.commitPublisher.gitea;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum GiteaBuildStatus {
  PENDING("pending"),
  SUCCESS("success"),
  ERROR("error"),
  FAILURE("failure");

  private static final Map<String, GiteaBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(val -> val.name(), Function.identity()));


  private final String myName;

  GiteaBuildStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public static GiteaBuildStatus getByName(String name) {
    return INDEX.get(name);
  }

}