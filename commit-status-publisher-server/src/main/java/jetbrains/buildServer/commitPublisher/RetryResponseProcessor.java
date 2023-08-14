package jetbrains.buildServer.commitPublisher;

import java.io.IOException;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import org.jetbrains.annotations.NotNull;

public class RetryResponseProcessor implements HttpResponseProcessor<HttpPublisherException> {

  @NotNull
  private final HttpResponseProcessor<HttpPublisherException> myDelegate;

  public static boolean shouldRetryOnCode(int statusCode) {
    return statusCode >= 500 || statusCode == 429;
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
