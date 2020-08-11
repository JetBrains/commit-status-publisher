package jetbrains.buildServer.commitPublisher.space;

import org.jetbrains.annotations.NotNull;

public enum SpaceBuildStatus {
  RUNNING("RUNNING"),
  FAILING("FAILING"),
  SUCCEEDED("SUCCEEDED"),
  FAILED("FAILED"),
  TERMINATED("TERMINATED");

  private final String myName;

  SpaceBuildStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
