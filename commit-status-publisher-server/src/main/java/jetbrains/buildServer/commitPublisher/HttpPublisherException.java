

package jetbrains.buildServer.commitPublisher;

import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 21/12/16.
 */
public class HttpPublisherException extends PublisherException {

  private final Integer myStatusCode;

  public HttpPublisherException(String message) {
    super(message);
    myStatusCode = null;
  }

  public HttpPublisherException(String message, Throwable t) {
    super(message, t);
    myStatusCode = null;
  }

  public HttpPublisherException(int statusCode, String reason) {
    this(statusCode, reason, null);
  }

  public HttpPublisherException(int statusCode, String reason, @Nullable String message) {
    super(String.format("%sresponse code: %d, reason: %s", null == message ? "" : message + ", ", statusCode, reason));
    myStatusCode = statusCode;
  }

  public Integer getStatusCode() {
    return myStatusCode;
  }
}