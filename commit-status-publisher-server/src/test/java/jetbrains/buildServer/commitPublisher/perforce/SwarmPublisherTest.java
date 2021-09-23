package jetbrains.buildServer.commitPublisher.perforce;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author kir
 */
public class SwarmPublisherTest extends HttpPublisherTest {

  public SwarmPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*viewQueued.html.*");
    myExpectedRegExps.put(EventToTest.REMOVED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*viewLog.html%3F.*started.*");

    myExpectedRegExps.put(EventToTest.INTERRUPTED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*interrupted.*");
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*failure%20was%20detected.*");
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*marked%20as%20successful.*");
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, myExpectedRegExps.get(EventToTest.MARKED_SUCCESSFUL));

    myExpectedRegExps.put(EventToTest.FAILED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*has%20finished%3A%20.*");
    myExpectedRegExps.put(EventToTest.FINISHED, myExpectedRegExps.get(EventToTest.FAILED));

    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*and%20%22");
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, "GET /api/v9/projects\\?fields=id HTTP/1.1");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myPublisherSettings = new SwarmPublisherSettings(new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider);
    Map<String, String> params = getPublisherParams();
    myPublisher = new SwarmPublisher((SwarmPublisherSettings)myPublisherSettings, myBuildType, FEATURE_ID, params, myProblems, myWebLinks);


    myBuildType.addParameter(new SimpleParameter("vcsRoot." + myVcsRoot.getExternalId() + ".shelvedChangelist", "1234321"));
  }

  protected SRunningBuild startBuildInCurrentBranch(SBuildType buildType) {
    return theBuild(buildType).run();
  }

  private BuildBuilder theBuild(SBuildType buildType) {
    return build().in(buildType).personalForUser("fedor");
  }

  protected SFinishedBuild createBuildInCurrentBranch(SBuildType buildType, Status status) {
    return theBuild(buildType).finish();
  }

  @NotNull
  @Override
  protected SQueuedBuild addBuildToQueue() {
    return theBuild(myBuildType).addToQueue();
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(SwarmPublisherSettings.PARAM_URL, getServerUrl());
      put(SwarmPublisherSettings.PARAM_USERNAME, "admin");
      put(SwarmPublisherSettings.PARAM_PASSWORD, "admin");
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    final String testConnectionURL = "/api/v9/projects?fields=id";
    if (url.contains(testConnectionURL)) {
      return true;
    }
    if (url.contains("/api/v9/reviews?fields=id&change[]=1234321")) {
      httpResponse.setEntity(new StringEntity("{\"lastSeen\":19,\"reviews\":[{\"id\":19}],\"totalCount\":1}", "UTF-8"));
      return true;
    }
    return false;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, HttpRequest httpRequest, HttpResponse httpResponse) {
    if (url.contains("/api/v9/comments") && requestData.contains("topic=reviews/19")) {
      httpResponse.setEntity(new StringEntity("{}", "UTF-8"));
      return true;
    }
    return false;
  }

  public void should_report_timeout_failure() throws Exception {
    setPublisherTimeout(10);
    myDoNotRespond = true;

    try {
      myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
      fail("Exception expected");
    } catch (PublisherException e) {
      then(e).hasMessageContaining("timed out").hasMessageContaining(myBuildType.getExtendedFullName());
    }
  }


  @Override
  @Test(enabled = false)
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    super.test_testConnection_fails_on_readonly();
  }

  @Override
  @Test(enabled = false)
  public void test_testConnection_fails_on_bad_repo_url() throws InterruptedException {
    super.test_testConnection_fails_on_bad_repo_url();
  }

  @Override
  @Test(enabled = false)
  public void test_testConnection_fails_on_missing_target() throws InterruptedException {
    super.test_testConnection_fails_on_missing_target();
  }

}
