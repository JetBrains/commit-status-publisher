package jetbrains.buildServer.commitPublisher;

import org.apache.http.HttpResponse;

import java.io.IOException;

/**
 * @author anton.zamolotskikh, 23/11/16.
 */
public interface HttpResponseProcessor {

  void processResponse(HttpResponse response) throws HttpPublisherException, IOException;

}
