package jetbrains.buildServer.commitPublisher;

import java.io.IOException;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;

public class RetryResponseProcessor implements HttpResponseProcessor<HttpPublisherException> {

  private final HttpResponseProcessor<HttpPublisherException> myProvidedHttpResponseProcessor;

  public static boolean shouldRetryOnCode(int statusCode) {
    return statusCode >= 500 || statusCode == 429;
  }

  public RetryResponseProcessor(HttpResponseProcessor<HttpPublisherException> httpResponseProcessor) {
    myProvidedHttpResponseProcessor = httpResponseProcessor;
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    try {
      myProvidedHttpResponseProcessor.processResponse(response);
    } catch (PublisherException ex) {
      if (shouldRetryOnCode(response.getStatusCode())) {
        ex.setShouldRetry();
        throw ex;
      }
    }
  }

  public HttpResponseProcessor<HttpPublisherException> getProcessor() {
    return myProvidedHttpResponseProcessor;
  }
}
