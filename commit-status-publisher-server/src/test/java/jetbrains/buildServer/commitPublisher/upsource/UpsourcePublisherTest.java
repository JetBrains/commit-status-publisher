package jetbrains.buildServer.commitPublisher.upsource;

import com.google.gson.Gson;
import java.nio.charset.Charset;
import java.util.Base64;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.upsource.data.UpsourceCurrentUser;
import jetbrains.buildServer.commitPublisher.upsource.data.UpsourceGetCurrentUserResult;
import org.apache.http.Header;
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
public class UpsourcePublisherTest extends HttpPublisherTest {

  public UpsourcePublisherTest() {
    myExpectedRegExps.put(Events.QUEUED, null); // not to be tested
    myExpectedRegExps.put(Events.REMOVED, null); // not to be tested
    myExpectedRegExps.put(Events.STARTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build started.*in_progress.*%s.*", REVISION));
    myExpectedRegExps.put(Events.FINISHED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Success.*success.*%s.*", REVISION));
    myExpectedRegExps.put(Events.FAILED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Failure.*failed.*%s.*", REVISION));
    myExpectedRegExps.put(Events.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.INTERRUPTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*%s.*failed.*%s.*", PROBLEM_DESCR, REVISION));
    myExpectedRegExps.put(Events.FAILURE_DETECTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*%s.*failed.*%s.*", PROBLEM_DESCR, REVISION));
    myExpectedRegExps.put(Events.MARKED_SUCCESSFUL, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build marked as successful.*success.*%s.*", REVISION));
    myExpectedRegExps.put(Events.MARKED_RUNNING_SUCCESSFUL, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build marked as successful.*in_progress.*%s.*", REVISION));
    myExpectedRegExps.put(Events.TEST_CONNECTION, String.format(".*~rpc/getCurrentUser.*"));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPublisherSettings = new UpsourceSettings(myFixture.getVcsHistory(), myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems);
    Map<String, String> params = getPublisherParams();
    myPublisher = new UpsourcePublisher(myBuildType, FEATURE_ID, myFixture.getVcsHistory(), myExecServices, myWebLinks, params, myProblems);
  }

  @Override
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    test_testConnection_failure(myReadOnlyVcsURL, getPublisherParams("PRJ2_READONLY", "user"));
  }

  @Override
  public void test_testConnection_fails_on_bad_repo_url() throws InterruptedException {
    // Irrelevant for Upsource publisher
  }

  @Override
  public void test_testConnection_fails_on_missing_target() throws InterruptedException {
    test_testConnection_failure("http://localhost/nouser/norepo", getPublisherParams("PRJ3_MISSING", "user"));
  }

  public void test_testConnection_fails_on_failed_authorisation() throws InterruptedException {
    test_testConnection_failure("http://localhost/nouser/norepo", getPublisherParams("PRJ1", "anotheruser"));
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return getPublisherParams("PRJ1", "user");
  }

  private Map<String, String> getPublisherParams(final String projectId, final String username) {
    return new HashMap<String, String>() {{
      put(Constants.UPSOURCE_PROJECT_ID, projectId);
      put(Constants.UPSOURCE_USERNAME, username);
      put(Constants.UPSOURCE_PASSWORD, "pwd");
      put(Constants.UPSOURCE_SERVER_URL, getServerUrl());
    }};
  }

  @Override
  protected void populateResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
    super.populateResponse(httpRequest, httpResponse);
    if (httpRequest.getRequestLine().getMethod().equals("GET") &&
        httpRequest.getRequestLine().getUri().contains(UpsourceSettings.ENDPOINT_RPC + "/" + UpsourceSettings.QUERY_GET_CURRENT_USER)) {
      final Header [] headers = httpRequest.getHeaders("Authorization");
      String username = null;
      if (headers.length > 0) {
        String auth = headers[0].getValue();
        if (auth.startsWith("Basic")) {
          String base64Credentials = auth.substring("Basic".length()).trim();
          String credentials = new String(Base64.getDecoder().decode(base64Credentials), Charset.forName("UTF-8"));
          final String[] values = credentials.split(":", 2);
          username = values[0];
        }
      }
      if (!"user".equals(username)) {
        httpResponse.setStatusCode(401);
        return;
      }
      Gson gson = new Gson();
      UpsourceGetCurrentUserResult result = new UpsourceGetCurrentUserResult();
      result.result = new UpsourceCurrentUser();
      result.result.userId = "54d6f22e-c7ae-4547-ab8a-650f975fcfde";
      result.result.isServerAdmin = false;
      result.result.adminPermissionsInProjects = new String[] {"PRJ1"};
      result.result.reviewViewPermissionsInProjects = new String[] {"PRJ2_READONLY"};
      String jsonResponse = gson.toJson(result);
      httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
    }
  }
}