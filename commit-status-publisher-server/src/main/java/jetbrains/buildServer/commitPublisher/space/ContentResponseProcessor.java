package jetbrains.buildServer.commitPublisher.space;

import jetbrains.buildServer.commitPublisher.HttpHelper;
import jetbrains.buildServer.commitPublisher.HttpPublisherException;
import jetbrains.buildServer.commitPublisher.HttpResponseProcessor;

import java.io.IOException;

public class ContentResponseProcessor implements HttpResponseProcessor {

  private String content;

  public String getContent() {
    return content;
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    int statusCode = response.getStatusCode();
    if (statusCode >= 400) {
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error: " + response.getContent());
    }

    content = response.getContent();
  }
}
