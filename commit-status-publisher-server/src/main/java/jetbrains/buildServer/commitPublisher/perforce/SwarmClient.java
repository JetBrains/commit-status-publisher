package jetbrains.buildServer.commitPublisher.perforce;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.util.StringUtil;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.perforce.SwarmPublisherSettings.*;

/**
 * @author kir
 */
public class SwarmClient {

  private final String mySwarmUrl;
  private final String myUsername;
  private final String myTicket;
  private int myConnectionTimeout;
  private final KeyStore myTrustStore;

  public SwarmClient(@NotNull Map<String, String> params, int connectionTimeout, @Nullable KeyStore trustStore) {
    myUsername = params.get(PARAM_USERNAME);
    myTicket = params.get(PARAM_PASSWORD);
    mySwarmUrl = StringUtil.removeTailingSlash(params.get(PARAM_URL));

    myConnectionTimeout = connectionTimeout;
    myTrustStore = trustStore;
  }

  public void setConnectionTimeout(int connectionTimeout) {
    myConnectionTimeout = connectionTimeout;
  }

  public void testConnection() throws PublisherException {
    final String projectsUrl = mySwarmUrl + "/api/v9/projects?fields=id";
    try {
      HttpHelper.get(projectsUrl, myUsername, myTicket, null, myConnectionTimeout, myTrustStore, new DefaultHttpResponseProcessor());
    } catch (IOException e) {
      throw new PublisherException("Cannot get list of projects from " + projectsUrl + " to test connection:" + e.getMessage(), e);
    }
  }

  @NotNull
  public List<Long> getReviewIds(@NotNull String changelistId, @NotNull String debugInfo) throws PublisherException {
    final String getReviewsUrl = mySwarmUrl + "/api/v9/reviews?fields=id&change[]=" + changelistId;
    try {
      final ReadReviewsProcessor processor = new ReadReviewsProcessor(debugInfo);
      HttpHelper.get(getReviewsUrl, myUsername, myTicket, null, myConnectionTimeout, myTrustStore, processor);

      return processor.getReviewIds();
    } catch (IOException e) {
      throw new PublisherException("Cannot get list of reviews from " + getReviewsUrl + " for " + debugInfo + ": " + e, e);
    }
  }

  public void addCommentToReview(@NotNull Long reviewId, @NotNull String fullComment, @NotNull String debugInfo) throws PublisherException {

    final String addCommentUrl = mySwarmUrl + "/api/v9/comments";

    String data = "topic=reviews/" + reviewId + "&body=" + StringUtil.encodeURLParameter(fullComment);
    try {
      HttpHelper.post(addCommentUrl, myUsername, myTicket,
                      data, ContentType.APPLICATION_FORM_URLENCODED, null, myConnectionTimeout, myTrustStore, new DefaultHttpResponseProcessor());
    } catch (IOException e) {
      throw new PublisherException("Cannot get list of reviews from " + addCommentUrl + " for " + debugInfo + ": " + e, e);
    }
  }

  private void info(String message) {
    LoggerUtil.LOG.info(message);
  }

  private void debug(String message) {
    LoggerUtil.LOG.info(message);
  }

  private class ReadReviewsProcessor implements HttpResponseProcessor {

    private final List<Long> myReviewIds = new ArrayList<>();
    private final String myDebugInfo;

    private ReadReviewsProcessor(@NotNull String debugInfo) {
      myDebugInfo = debugInfo;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      if (response.getStatusCode() >= 400) {
        throw new HttpPublisherException(response.getStatusCode(), response.getStatusText(), "Cannot get list of related reviews for " + myDebugInfo);
      }

      // response = {"lastSeen":19,"reviews":[{"id":19}],"totalCount":1}
      debug("Reviews response for " + myDebugInfo + " = " + response.getContent() + " " + response.getStatusCode() + " " + response.getStatusText());

      try {
        final JsonNode jsonNode = new ObjectMapper().readTree(response.getContent());
        final ArrayNode reviews = (ArrayNode)jsonNode.get("reviews");

        if (reviews != null) {
          for (Iterator<JsonNode> it = reviews.elements(); it.hasNext(); ) {
            JsonNode element = it.next();
            myReviewIds.add(element.get("id").longValue());
          }
        }
        if (myReviewIds.size() > 0) {
          info(String.format("Found Perforce Swarm reviews %s for %s", myReviewIds, myDebugInfo));
        }

      } catch (JsonProcessingException e) {
        throw new HttpPublisherException("Error parsing JSON response from Perforce Swarm: " + e.getMessage(), e);
      }
    }

    @NotNull
    public List<Long> getReviewIds() {
      return myReviewIds;
    }
  }

}
