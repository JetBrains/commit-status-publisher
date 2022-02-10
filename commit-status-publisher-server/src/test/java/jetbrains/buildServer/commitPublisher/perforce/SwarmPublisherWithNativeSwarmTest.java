package jetbrains.buildServer.commitPublisher.perforce;

import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author kir
 */
public class SwarmPublisherWithNativeSwarmTest extends HttpPublisherTest {

  private static final String CHANGELIST = "1234321";
  private boolean myCreatePersonal;

  public SwarmPublisherWithNativeSwarmTest() {
    System.setProperty("teamcity.dev.test.retry.count", "0");
    
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"completedTime\":\".*\",\"messages\":\\[\"Failure\"\\],\"status\":\"fail\"}");
    myExpectedRegExps.put(EventToTest.STARTED, "POST /api/v10/reviews/19/testruns HTTP/1.1\tENTITY:[\\s\\S]+");

    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"messages\":\\[\"Problem description \\(new\\)\"],\"status\":\"running\"}");
    myExpectedRegExps.put(EventToTest.INTERRUPTED, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"completedTime\":\"\\d+\",\"messages\":\\[\"Problem description \\(new\\)\"\\],\"status\":\"fail\"}");

    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"completedTime\":\"\\d+\",\"messages\":\\[\"Success\"\\],\"status\":\"pass\"}");
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"messages\":\\[\"Running\"\\],\"status\":\"running\"}");

    myExpectedRegExps.put(EventToTest.FAILED, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"completedTime\":\"\\d+\",\"messages\":\\[\"Failure\"\\],\"status\":\"fail\"}");
    myExpectedRegExps.put(EventToTest.FINISHED, "POST /api/v10/reviews/19/testruns/706 HTTP/1.1\tENTITY: \\{\"completedTime\":\"\\d+\",\"messages\":\\[\"Success\"\\],\"status\":\"pass\"}");

  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myPublisherSettings = new SwarmPublisherSettings(new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider);
    Map<String, String> params = getPublisherParams();
    myPublisher = new SwarmPublisher((SwarmPublisherSettings)myPublisherSettings, myBuildType, FEATURE_ID, params, myProblems, myWebLinks);


    myBuildType.addParameter(new SimpleParameter("vcsRoot." + myVcsRoot.getExternalId() + ".shelvedChangelist", CHANGELIST));
    myCreatePersonal = true;
  }

  protected SRunningBuild startBuildInCurrentBranch(SBuildType buildType) {
    return theBuild(buildType).run();
  }

  private BuildBuilder theBuild(SBuildType buildType) {
    final BuildBuilder result = build().in(buildType);
    return myCreatePersonal ? result.personalForUser("fedor") : result;
  }

  protected SFinishedBuild createBuildInCurrentBranch(SBuildType buildType, Status status) {
    return status.isSuccessful() ? theBuild(buildType).finish() : theBuild(buildType).failed().finish();
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
      put(SwarmPublisherSettings.PARAM_CREATE_SWARM_TEST, "true");
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/api/v9/reviews?fields=id&change[]=" + CHANGELIST + "&state[]=needsReview")) {
      httpResponse.setEntity(new StringEntity("{\"lastSeen\":19,\"reviews\":[{\"id\":19}],\"totalCount\":1}", "UTF-8"));
      return true;
    }
    if (url.contains("/api/v10/reviews/19/testruns")) {
      String responseJson = "perforce/sampleTestRunsResponse.json";
      try (InputStream stream = getClass().getClassLoader().getResourceAsStream(responseJson)) {
        String responseTemplate = new String(StreamUtil.loadFromStream(stream));

        // Have to use correct test name in the template
        String response = responseTemplate.replace("TESTNAME_VAR", myBuildType.getExternalId());
        httpResponse.setEntity(new StringEntity(response));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return true;
    }
    return false;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, HttpRequest httpRequest, HttpResponse httpResponse) {
    if (url.contains("/api/v9/login")) {
      // Tested in SwarmPublisherTest
      return true;
    }

    // Add some comment to a review
    if (url.contains("/api/v9/comments") && requestData.contains("topic=reviews/19")) {
      httpResponse.setEntity(new StringEntity("{}", "UTF-8"));
      return true;
    }

    // Create test run call
    if (url.equals("/api/v10/reviews/19/testruns")) {
      httpResponse.setEntity(new StringEntity("{}", "UTF-8"));
      return true;
    }

    // Update existing test run call
    if (url.contains("/api/v10/reviews/19/testruns/706")) {
      httpResponse.setEntity(new StringEntity("{}", "UTF-8"));
      return true;
    }
    return false;
  }

  @Test(enabled = false)
  @Override
  public void test_buildQueued() throws Exception {
    super.test_buildQueued();
  }

  @Test(enabled = false)
  @Override
  public void test_buildRemovedFromQueue() throws Exception {
    super.test_buildRemovedFromQueue();
  }

  @Test(enabled = false)
  @Override
  public void should_report_timeout_failure() throws Exception {
    super.should_report_timeout_failure();
  }

  @Test(enabled = false)
  @Override
  public void test_testConnection() throws Exception {
    super.test_testConnection();
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
