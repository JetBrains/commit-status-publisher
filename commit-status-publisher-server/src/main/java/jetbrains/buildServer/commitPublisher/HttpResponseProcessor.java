package jetbrains.buildServer.commitPublisher;

import java.io.IOException;

/**
 * @author anton.zamolotskikh, 23/11/16.
 */
public interface HttpResponseProcessor {

  void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException;

}
