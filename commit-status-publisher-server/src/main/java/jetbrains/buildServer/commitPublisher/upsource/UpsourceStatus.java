package jetbrains.buildServer.commitPublisher.upsource;

import org.jetbrains.annotations.NotNull;

public enum UpsourceStatus {

  IN_PROGRESS("in_progress"),
  SUCCESS("success"),
  FAILED("failed");

  private final String myName;

  UpsourceStatus(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
