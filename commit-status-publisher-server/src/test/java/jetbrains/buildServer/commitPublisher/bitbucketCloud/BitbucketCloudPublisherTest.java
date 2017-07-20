package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import com.google.gson.Gson;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudRepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class BitbucketCloudPublisherTest extends HttpPublisherTest {

  public BitbucketCloudPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*INPROGRESS.*Build started.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*SUCCESSFUL.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS,
                          String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*SUCCESSFUL.*Success with a comment by %s:.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED,
                          String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*FAILED.*Failure with a comment by %s:.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS,
                          String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*INPROGRESS.*Running with a comment by %s:.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED,
                          String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*FAILED.*%s.*with a comment by %s:.*%s.*", REVISION, PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*STOPPED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*SUCCESSFUL.*Build marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*INPROGRESS.*Build marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*2.0/repositories/owner/project.*");
  }

  @Override
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    // NOTE: Bitbucket Cloud Publisher cannot determine if it has just read only access during connection testing
  }

  public void test_testConnection_with_mercurial() throws Exception {
    SVcsRoot vcsRoot = myFixture.addVcsRoot("mercurial", "", myBuildType);
    vcsRoot.setProperties(Collections.singletonMap("repositoryPath", "http://owner@localhost/" + OWNER + "/" + CORRECT_REPO));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(vcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    myPublisherSettings.testConnection(myBuildType, vcsRoot, getPublisherParams());
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(EventToTest.TEST_CONNECTION));
  }

  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getMessage()).matches(".*failed to parse repository URL.*" + myVcsRoot.getName() + ".*");
    }
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.endsWith("/repositories/" + OWNER + "/" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
    } else if (url.endsWith("/repositories/" + OWNER + "/" + READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }


  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    Gson gson = new Gson();
    BitbucketCloudRepoInfo repoInfo = new BitbucketCloudRepoInfo();
    repoInfo.slug = repoName;
    repoInfo.type = "repository";
    repoInfo.is_private = true;
    repoInfo.description = "";
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = getPublisherParams();
    myPublisherSettings = new BitbucketCloudSettings(myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems);
    BitbucketCloudPublisher publisher = new BitbucketCloudPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
    publisher.setBaseUrl(getServerUrl() + "/");
    ((BitbucketCloudSettings)myPublisherSettings).setDefaultApiUrl(getServerUrl() + "/");
    myPublisher = publisher;
    setExpectedApiPath("/2.0");
    setExpectedEndpointPrefix("/repositories/" + OWNER + "/" + CORRECT_REPO);
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.BITBUCKET_CLOUD_USERNAME, "user");
      put(Constants.BITBUCKET_CLOUD_PASSWORD, "pwd");
    }};
  }
}