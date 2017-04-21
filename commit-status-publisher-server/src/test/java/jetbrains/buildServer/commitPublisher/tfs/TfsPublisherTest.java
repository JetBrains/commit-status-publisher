package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.04.2017
 *         Time: 18:06
 */
public class TfsPublisherTest extends HttpPublisherTest {

  TfsPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format("POST /_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Pending.*is pending.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format("POST /_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Succeeded.*has succeeded.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format("POST /_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Failed.*has failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format("POST /_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Failed.*has failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format("POST /_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Succeeded.*has succeeded.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format("POST /_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Pending.*is pending.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, "GET /_apis/git/repositories/project/commits.*\\$top=1.*"); // not to be tested
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPublisherSettings = new TfsPublisherSettings(myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems,
      myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext());
    Map<String, String> params = getPublisherParams();
    myPublisher = new TfsStatusPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
    myVcsURL = getServerUrl() + "/_git/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl()  + "/_git/" + READ_ONLY_REPO;
    myVcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
  }

  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getMessage()).matches("Invalid Git server URL.*" + myVcsRoot.getProperty("url") + ".*");
    }
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
      if (httpRequest.getRequestLine().getUri().contains(READ_ONLY_REPO)) {
        httpResponse.setStatusCode(403);
        httpResponse.setEntity(new StringEntity("{'message': 'error'}", "UTF-8"));
      }
    }
  }
}
