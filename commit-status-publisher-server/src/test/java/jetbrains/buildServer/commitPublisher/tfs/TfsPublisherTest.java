package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.04.2017
 *         Time: 18:06
 */
public class TfsPublisherTest extends HttpPublisherTest {

  TfsPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build started.*in_progress.*%s.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Success.*success.*%s.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Failure.*failed.*%s.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*%s.*failed.*%s.*", PROBLEM_DESCR, REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*%s.*failed.*%s.*", PROBLEM_DESCR, REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build marked as successful.*success.*%s.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build marked as successful.*in_progress.*%s.*", REVISION));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, String.format(".*~buildStatusTestConnection.*\"project\":\"PRJ1\".*"));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPublisherSettings = new TfsPublisherSettings(myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems,
      myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext());
    Map<String, String> params = getPublisherParams();
    myPublisher = new TfsStatusPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(TfsConstants.ACCESS_TOKEN, "token");
    }};
  }

  @Override
  protected void populateResponse(HttpRequest httpRequest, String requestData, HttpResponse httpResponse) {
    super.populateResponse(httpRequest, requestData, httpResponse);
    if (httpRequest.getRequestLine().getMethod().equals("GET")) {
      if (httpRequest.getRequestLine().getUri().endsWith(OWNER + "/repos/" + CORRECT_REPO)) {
        httpResponse.setEntity(new StringEntity("", "UTF-8"));
      } else if (httpRequest.getRequestLine().getUri().endsWith(OWNER + "/repos/" + READ_ONLY_REPO)) {
        httpResponse.setEntity(new StringEntity("", "UTF-8"));
      }
    }
  }
}
