package jetbrains.buildServer.swarm.commitPublisher;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.commitPublisher.AdditionalTaskInfo;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.util.TestFor;
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

  private static final String CHANGELIST = "1234321";
  private boolean myCreatePersonal;
  private boolean myNonAdmin;
  private boolean myCreateSwarmTestRun;

  public SwarmPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*viewQueued.html.*");
    myExpectedRegExps.put(EventToTest.MERGED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*viewQueued.html.*" + COMMENT + ".*");
    myExpectedRegExps.put(EventToTest.REMOVED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*viewLog.html%3F.*removed.*" + COMMENT + ".*");
    myExpectedRegExps.put(EventToTest.STARTED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*viewLog.html%3F.*started.*");

    myExpectedRegExps.put(EventToTest.INTERRUPTED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*interrupted.*");
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*failure%20was%20detected.*");
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*marked%20as%20successful.*");
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, myExpectedRegExps.get(EventToTest.MARKED_SUCCESSFUL));

    myExpectedRegExps.put(EventToTest.FAILED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*has%20failed.*");
    myExpectedRegExps.put(EventToTest.FINISHED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*has%20finished%20successfully.*");

    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, "POST /api/v9/comments HTTP/1.1\tENTITY: topic=reviews/19.*and%20%22");
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, "POST /api/v9/login HTTP/1.1.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    SwarmClientManager clientManager = new SwarmClientManager(myWebLinks, () -> null);
    myPublisherSettings = new SwarmPublisherSettings(new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider, clientManager);

    Map<String, String> params = getPublisherParams();
    myPublisher = new SwarmPublisher((SwarmPublisherSettings)myPublisherSettings, myBuildType, FEATURE_ID, params, myProblems, myWebLinks,
                                     clientManager.getSwarmClient(params));


    addShelvedChangelistParameter(CHANGELIST);
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
    HashMap<String, String> result = new HashMap<String, String>() {{
      put(SwarmPublisherSettings.PARAM_URL, getServerUrl());
      put(SwarmPublisherSettings.PARAM_USERNAME, myNonAdmin ? "user" : "admin");
      put(SwarmPublisherSettings.PARAM_PASSWORD, myNonAdmin ? "user" : "admin");
    }};
    if (myCreateSwarmTestRun) {
      result.put(SwarmPublisherSettings.PARAM_CREATE_SWARM_TEST, "true");
    }
    return result;
  }

  public void test_testConnection_OK_NonAdmin() throws Exception {
    myNonAdmin = true;
    myCreateSwarmTestRun = false;
    super.test_testConnection();
  }

  public void test_testConnection_Fail_NonAdmin_WhenRequired() throws Exception {
    myNonAdmin = true;
    myCreateSwarmTestRun = true;

    try {
      myPublisherSettings.testConnection(myBuildType, myVcsRoot, getPublisherParams());
      fail("Connection testing failure must throw PublishError exception");
    } catch (PublisherException ex) {
      // success
      then(ex).hasMessageContaining("lack admin permissions");
    }
  }

  @Override
  public void test_buildRemovedFromQueue() throws Exception {
    SQueuedBuild build = addBuildToQueue();
    build.removeFromQueue(myUser, null);
    myPublisher.buildRemovedFromQueue(build.getBuildPromotion(), myRevision, new AdditionalTaskInfo(COMMENT, myUser, null));
    then(getRequestAsString()).isNotNull().matches(myExpectedRegExps.get(EventToTest.REMOVED));
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    final String testConnectionURL = "/api/v9/projects?fields=id";
    if (url.contains(testConnectionURL)) {
      return true;
    }
    if (url.contains("/api/v9/reviews?fields=id,state,stateLabel&change[]=" + CHANGELIST)) {
      httpResponse.setEntity(new StringEntity("{\"lastSeen\":19,\"reviews\":[{\"id\":19,\"state\":\"needsReview\"},{\"id\":29,\"state\":\"approved\"}],\"totalCount\":1}", "UTF-8"));
      return true;
    }
    return false;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, HttpRequest httpRequest, HttpResponse httpResponse) {
    if (url.contains("/api/v9/login")) {
      if (requestData.contains("username=admin&password=admin")) {
        // Admin login
        httpResponse.setEntity(new StringEntity("{\"user\":{\"isAdmin\":true}}", "UTF-8"));
      }
      else if (requestData.contains("username=user&password=user")) {
        // Non-admin login
        httpResponse.setEntity(new StringEntity("{\"user\":{\"isAdmin\":false}}", "UTF-8"));
      }
      return true;
    }

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

  @Test
  public void should_report_on_non_personal_build_with_ordinary_changelist() throws Exception {
    myCreatePersonal = false;
    myBuildType.getParametersCollection().forEach((p) -> myBuildType.removeParameter(p.getName()));

    myRevision = new BuildRevision(myBuildType.getVcsRootInstanceForParent(myVcsRoot), CHANGELIST, "", CHANGELIST);
    
    test_buildStarted();
  }

  @Test
  public void should_not_report_on_personal_build_with_ordinary_changelist_without_shelve_param() throws Exception {
    myCreatePersonal = true;
    myBuildType.getParametersCollection().forEach((p) -> myBuildType.removeParameter(p.getName()));

    myRevision = new BuildRevision(myBuildType.getVcsRootInstanceForParent(myVcsRoot), CHANGELIST, "", CHANGELIST);

    myPublisher.buildStarted(startBuildInCurrentBranch(myBuildType), myRevision);

    then(getRequestAsString()).isNull();
  }

  @Test
  @TestFor(issues = "TW-76574")
  public void should_not_report_on_personal_build_with_empty_shelve_param() throws Exception {
    myCreatePersonal = true;
    myBuildType.getParametersCollection().forEach((p) -> myBuildType.removeParameter(p.getName()));
    addShelvedChangelistParameter("");

    myRevision = new BuildRevision(myBuildType.getVcsRootInstanceForParent(myVcsRoot), CHANGELIST, "", CHANGELIST);

    myPublisher.buildStarted(startBuildInCurrentBranch(myBuildType), myRevision);

    then(getRequestAsString()).isNull();
  }

  private void addShelvedChangelistParameter(@NotNull String value) {
    myBuildType.addParameter(new SimpleParameter("vcsRoot." + myVcsRoot.getExternalId() + ".shelvedChangelist", value));
  }

  protected void test_testConnection_failure(String repoURL, Map <String, String> params) throws InterruptedException {
    params.put(SwarmPublisherSettings.PARAM_URL, repoURL);
    try {
      myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
      fail("Connection testing failure must throw PublishError exception");
    } catch (PublisherException ex) {
      // success
    }
  }

  @Test(enabled = false)
  @Override
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    super.test_testConnection_fails_on_readonly();
  }
}
