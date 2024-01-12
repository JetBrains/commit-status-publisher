

package jetbrains.buildServer.commitPublisher;
import org.jetbrains.annotations.NotNull;

public class PublisherException extends Exception {

  private boolean myShouldRetry = false;

  public PublisherException(@NotNull String message) {
    super(message);
  }

  public PublisherException(@NotNull String message, Throwable cause) {
    super(message, cause);
    if (cause instanceof PublisherException) {
      myShouldRetry = ((PublisherException)cause).myShouldRetry;
    }
  }

  /**
   * Indicates that we should retry publishing status in {@link CommitStatusPublisherListener}
   */
  public PublisherException setShouldRetry() {
    myShouldRetry = true;
    return this;
  }

  public boolean shouldRetry() {
    return myShouldRetry;
  }

}