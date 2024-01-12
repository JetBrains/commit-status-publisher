

package jetbrains.buildServer.commitPublisher;

import java.io.IOException;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;

/**
 * @author anton.zamolotskikh, 27/11/16.
 */
public class DefaultHttpResponseProcessor implements HttpResponseProcessor<HttpPublisherException> {
  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400)
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error");
  }
}