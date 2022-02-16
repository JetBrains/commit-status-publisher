package jetbrains.buildServer.commitPublisher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class ResponseEntityProcessor<T> extends DefaultHttpResponseProcessor {
  private static final String JSON_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  private final Class<T> myType;
  private final Gson myGson;

  private T myResult;

  public ResponseEntityProcessor(Class<T> type) {
    myType = type;
    myGson = new GsonBuilder().setDateFormat(JSON_DATE_TIME_FORMAT).create();
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    boolean shouldContinueProcessing = handleError(response);
    if (!shouldContinueProcessing) {
      myResult = null;
      return;
    }
    final String content = response.getContent();
    if (content == null) {
      throw new HttpPublisherException("Unexpected empty content in reponse");
    }
    try {
      myResult = myGson.fromJson(content, myType);
    } catch (JsonSyntaxException e) {
      throw new HttpPublisherException("Invalid response: " + e.getMessage(), e);
    }
  }

  /**
   * Method defines how HTTP errors should be processed and what should be done with following processing in case of error
   * @return true if processing can be continued, otherwise - false
   * @throws HttpPublisherException in case, when processing should not be continued any way
   */
  protected boolean handleError(@NotNull HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    super.processResponse(response);
    return true;
  }

  public T getProcessingResult() {
    return myResult;
  }
}
