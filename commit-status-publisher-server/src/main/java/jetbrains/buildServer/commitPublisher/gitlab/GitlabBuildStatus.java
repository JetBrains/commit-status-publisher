package jetbrains.buildServer.commitPublisher.gitlab;

import org.jetbrains.annotations.NotNull;

public enum GitlabBuildStatus {
  PENDING("pending"),
  RUNNING("running"),
  SUCCESS("success"),
  FAILED("failed"),
  CANCELED("canceled");

  private final String myName;

  GitlabBuildStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
