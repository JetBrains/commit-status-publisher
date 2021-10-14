package jetbrains.buildServer.commitPublisher.perforce;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.security.KeyStore;
import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.http.HttpMethod;
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
      throw new PublisherException("Cannot add a comment for review at " + addCommentUrl + " for " + debugInfo + ": " + e, e);
    }
  }


  // https://www.perforce.com/manuals/swarm/Content/Swarm/swarm-apidoc_endpoint_integration_tests.html#Create_a__testrun_for_a_review_version
  public void createSwarmTestRun(long reviewId, @NotNull SBuild build, @NotNull String debugBuildInfo) throws PublisherException {
    final String createTestRunUrl = mySwarmUrl + "/api/v10/reviews/" + reviewId + "/testruns";

    try {
      HttpHelper.post(createTestRunUrl, myUsername, myTicket,
                      createTestRunJson(reviewId, build),
                      ContentType.APPLICATION_JSON, null, myConnectionTimeout, myTrustStore, new DefaultHttpResponseProcessor());
    } catch (IOException e) {
      throw new PublisherException("Cannot create test run at " + createTestRunUrl + " for " + debugBuildInfo + ": " + e, e);
    }
  }

  private static String createTestRunJson(long reviewId, SBuild build) {

    return String.format("{\n" +
                         "  \"change\": %d,\n" +
                         "  \"version\": 1,\n" +
                         "  \"test\": \"%s\",\n" +
                         "  \"startTime\": %d,\n" +
                         "  \"status\": \"running\"\n" +
                         "}",
                         reviewId,
                         testNameFrom(build),
                         build.getServerStartDate().getTime());
  }

  @NotNull
  private static String testNameFrom(SBuild build) {
    return StringUtil.truncateStringValueWithDotsAtCenter(build.getBuildTypeExternalId(), 32)
                     .replaceAll("\\.", "_");
  }

  public void updateSwarmTestRuns(long reviewId, @NotNull SBuild build, @NotNull String debugBuildInfo) throws PublisherException {

    final List<Long> testRunIds = collectTestRunIds(reviewId, build, debugBuildInfo);

    for (Long testRunId : testRunIds) {
      final Long completedTime = build.getFinishDate() != null ? build.getFinishDate().getTime() : null;
      final String status = build.getBuildStatus().isSuccessful() ? "pass" : "fail";

      changeTestRunStatus(reviewId, testRunId, completedTime, status, debugBuildInfo);
    }
  }

  @NotNull
  private List<Long> collectTestRunIds(long reviewId, @NotNull SBuild build, @NotNull String debugBuildInfo) throws PublisherException {
    final String testRunUrl = mySwarmUrl + "/api/v10/reviews/" + reviewId + "/testruns";

    final GetRunningTestRuns processor = new GetRunningTestRuns(testNameFrom(build), debugBuildInfo);
    try {
      HttpHelper.get(testRunUrl, myUsername, myTicket, null, myConnectionTimeout, myTrustStore, processor);
    } catch (IOException e) {
      throw new PublisherException("Cannot get test run list at " + testRunUrl + " for " + debugBuildInfo + ": " + e, e);
    }
    return processor.getTestRunIds();
  }

  // https://www.perforce.com/manuals/swarm/Content/Swarm/swarm-apidoc_endpoint_integration_tests.html#Update_details_for_a_testrun_-_PATCH
  private void changeTestRunStatus(long reviewId, @NotNull Long testRunId, @Nullable Long completedTime, @NotNull String status, @NotNull String debugBuildInfo)
    throws PublisherException {
    final String patchTestRunUrl = mySwarmUrl + "/api/v10/reviews/" + reviewId + "/testruns/" + testRunId;

    try {
      HttpHelper.http(HttpMethod.PATCH, patchTestRunUrl, myUsername, myTicket,
                      buildJsonForUpdate(completedTime, status),
                      ContentType.APPLICATION_JSON, null, myConnectionTimeout, myTrustStore, new DefaultHttpResponseProcessor());
    } catch (IOException e) {
      throw new PublisherException("Cannot update test run at " + patchTestRunUrl + " for " + debugBuildInfo + ": " + e, e);
    }
  }

  private static String buildJsonForUpdate(@Nullable Long completedTime, String status) {
    final HashMap<String, String> data = new HashMap<String, String>();
    data.put("status", status);
    if (completedTime != null) {
      data.put("completedTime", completedTime.toString());
    }
    try {
      return new ObjectMapper().writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
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
        if (response.getStatusCode() == 401 || response.getStatusCode() == 403) {
          throw new HttpPublisherException(response.getStatusCode(), response.getStatusText(),
                                           "Cannot access Perforce Swarm Server at '" + mySwarmUrl + "' to add details for " + myDebugInfo);
        }
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

  private class GetRunningTestRuns implements HttpResponseProcessor {

    private final List<Long> myTestRunIds = new ArrayList<>();
    private final String myDebugInfo;
    private final String myExpectedTestName;

    private GetRunningTestRuns(@NotNull String expectedTestName, @NotNull String debugInfo) {
      myExpectedTestName = expectedTestName;
      myDebugInfo = debugInfo;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      if (response.getStatusCode() >= 400) {
        if (response.getStatusCode() == 401 || response.getStatusCode() == 403) {
          throw new HttpPublisherException(response.getStatusCode(), response.getStatusText(),
                                           "Cannot access Perforce Swarm Server at '" + mySwarmUrl + "' to add details for " + myDebugInfo);
        }
        throw new HttpPublisherException(response.getStatusCode(), response.getStatusText(), "Cannot get the list of review testruns for " + myDebugInfo);
      }

      /*
      {
  "error": null,
  "messages": [],
  "data" : {
    "testruns" : [
      {
        "id": 706,
        "change": 12345,
        "version": 2,
        "test": "global1",
        "startTime": 1567895432,
        "completedTime": 1567895562,
        "status": "pass",
        "messages": [
          "Test completed successfully",
          "another message"
        ],
        "url": "http://my.jenkins.com/projectx/main/1224"
        "uuid": "FAE4501C-E4BC-73E4-A11A-FF710601BC3F"
      },
      {
        <ids for other testruns of the review, formatted as above>
      }
    ]
  }
}
       */

      debug("Test runs response for " + myDebugInfo + " = " + response.getContent() + " " + response.getStatusCode() + " " + response.getStatusText());

      try {
        final JsonNode jsonNode = new ObjectMapper().readTree(response.getContent());
        final JsonNode dataNode = jsonNode.get("data");
        if (dataNode == null) {
          return;
        }

        final ArrayNode testruns = (ArrayNode)dataNode.get("testruns");

        if (testruns != null) {
          for (Iterator<JsonNode> it = testruns.elements(); it.hasNext(); ) {
            JsonNode element = it.next();
            // Collect test runs whose test name match external build configuration ID and which are not completed
            final JsonNode completedTime = element.get("completedTime");
            if (myExpectedTestName.equals(element.get("test").textValue()) && (completedTime == null || completedTime.isNull())) {
              myTestRunIds.add(element.get("id").longValue());
            }
          }
        }
        if (myTestRunIds.size() > 0) {
          info(String.format("Found Perforce Swarm testruns %s for %s", myTestRunIds, myDebugInfo));
        }

      } catch (JsonProcessingException e) {
        throw new HttpPublisherException("Error parsing JSON response from Perforce Swarm: " + e.getMessage(), e);
      }
    }

    @NotNull
    public List<Long> getTestRunIds() {
      return myTestRunIds;
    }
  }

}
