package jetbrains.buildServer.commitPublisher;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;

import java.io.IOException;

/**
 * @author anton.zamolotskikh, 27/11/16.
 */
public class DefaultHttpResponseProcessor implements HttpResponseProcessor {
  @Override
  public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {
    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() >= 400)
      throw new HttpPublisherException(statusLine.getStatusCode(), statusLine.getReasonPhrase(), "HTTP response error");
  }
}
