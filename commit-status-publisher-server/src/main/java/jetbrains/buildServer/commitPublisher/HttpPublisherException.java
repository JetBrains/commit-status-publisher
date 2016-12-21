package jetbrains.buildServer.commitPublisher;

import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 21/12/16.
 */
public class HttpPublisherException extends PublisherException {

  public HttpPublisherException(String message) {
    super(message);
  }

  public HttpPublisherException(String message, Throwable t) {
    super(message, t);
  }

  public HttpPublisherException(int statusCode, String reason) {
    this(statusCode, reason, null);
  }

  public HttpPublisherException(int statusCode, String reason, @Nullable String message) {
    super(String.format("%sresponse code: %d, reason: %s", null == message ? "" : message + ", ", statusCode, reason));
  }
}
