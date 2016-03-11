package jetbrains.buildServer.commitPublisher;

import org.jetbrains.annotations.NotNull;

public class PublishError extends RuntimeException {

  public PublishError(@NotNull String message) {
    super(message);
  }

  public PublishError(@NotNull String message, Throwable cause) {
    super(message, cause);
  }

}
