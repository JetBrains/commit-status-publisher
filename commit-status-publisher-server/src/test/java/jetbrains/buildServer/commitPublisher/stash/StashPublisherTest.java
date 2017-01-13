package jetbrains.buildServer.commitPublisher.stash;

import com.google.gson.Gson;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.stash.data.StashRepoInfo;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class StashPublisherTest extends HttpPublisherTest {

  public StashPublisherTest() {
    myExpectedRegExps.put(Events.QUEUED, null); // not to be tested
    myExpectedRegExps.put(Events.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(Events.STARTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build started.*", REVISION));
    myExpectedRegExps.put(Events.FINISHED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success.*", REVISION));
    myExpectedRegExps.put(Events.FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(Events.COMMENTED_SUCCESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(Events.COMMENTED_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Running with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*with a comment by %s.*%s.*", REVISION, PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(Events.INTERRUPTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(Events.FAILURE_DETECTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(Events.MARKED_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*marked as successful.*", REVISION));
    myExpectedRegExps.put(Events.MARKED_RUNNING_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build marked as successful.*", REVISION));
    myExpectedRegExps.put(Events.TEST_CONNECTION, ".*api/.*/owner/repos/project.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = getPublisherParams();
    myPublisherSettings = new StashSettings(myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems);
    myPublisher = new StashPublisher(myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
  }

  @Override
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    // NOTE: Stash Publisher cannot determine if it has just read only access during connection testing
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.STASH_USERNAME, "user");
      put(Constants.STASH_PASSWORD, "pwd");
      put(Constants.STASH_BASE_URL, getServerUrl());
    }};
  }

  @Override
  protected void populateResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
    super.populateResponse(httpRequest, httpResponse);
    if (httpRequest.getRequestLine().getMethod().equals("GET")) {
      if (httpRequest.getRequestLine().getUri().endsWith(OWNER + "/repos/" + CORRECT_REPO)) {
        respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
      } else if (httpRequest.getRequestLine().getUri().endsWith(OWNER + "/repos/" + READ_ONLY_REPO)) {
        respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
      }
    }
  }

  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    Gson gson = new Gson();
    StashRepoInfo repoInfo = new StashRepoInfo();
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

}