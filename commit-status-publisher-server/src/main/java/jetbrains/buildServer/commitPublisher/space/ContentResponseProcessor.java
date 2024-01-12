

package jetbrains.buildServer.commitPublisher.space;

import java.io.IOException;
import jetbrains.buildServer.commitPublisher.HttpPublisherException;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;

public class ContentResponseProcessor implements HttpResponseProcessor<HttpPublisherException> {

  private String content;

  public String getContent() {
    return content;
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    int statusCode = response.getStatusCode();
    String responseContent = response.getContent();

    if (statusCode >= 400) {
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error: " + (responseContent != null ? responseContent : "<empty>"));
    }

    content = responseContent;
  }
}