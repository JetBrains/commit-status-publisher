package jetbrains.buildServer.commitPublisher;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import org.jetbrains.annotations.NotNull;

public class RetryResponseProcessor implements HttpResponseProcessor<HttpPublisherException> {

  @NotNull
  private static final HashSet<Integer> ourRetryableResponseCodes = new HashSet<>(Arrays.asList(
    408, // Request Timeout
    425, // Too Early
    429, // Too Many Requests
    500, // Internal Server Error
    502, // Bad Gateway
    503, // Service Unavailable
    504  // Gateway Timeout
  ));
  @NotNull
  private final HttpResponseProcessor<HttpPublisherException> myDelegate;

  public static boolean shouldRetryOnCode(int statusCode) {
    return ourRetryableResponseCodes.contains(statusCode);
  }

  public static void processNetworkException(@NotNull Throwable cause, @NotNull PublisherException ex) {
    if (cause instanceof IOException) {
      ex.setShouldRetry();
    }
  }

  public RetryResponseProcessor(@NotNull HttpResponseProcessor<HttpPublisherException> httpResponseProcessor) {
    myDelegate = httpResponseProcessor;
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    try {
      myDelegate.processResponse(response);
    } catch (PublisherException ex) {
      if (shouldRetryOnCode(response.getStatusCode())) {
        ex.setShouldRetry();
        throw ex;
      }
    }
  }

  public HttpResponseProcessor<HttpPublisherException> getProcessor() {
    return myDelegate;
  }
}
