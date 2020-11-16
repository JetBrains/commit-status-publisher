package jetbrains.buildServer.commitPublisher.space;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConstants;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthKeys;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.commitPublisher.space.SpaceToken.ACCESS_TOKEN_FIELD_NAME;
import static jetbrains.buildServer.commitPublisher.space.SpaceToken.TOKEN_TYPE_FIELD_NAME;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class SpacePublisherTest extends HttpPublisherTest {

  private static final String FAKE_CLIENT_ID = "clientid";
  private static final String FAKE_CLIENT_SECRET = "clientsecret";
  private String myProjectFeatureId;
  private Gson myGson;

  public SpacePublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*RUNNING.*Build started.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SUCCEEDED.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*TERMINATED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*FAILING.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SUCCEEDED.*marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*RUNNING.*Build marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*/projects/key:owner/commit-statuses/check-service.*");
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*FAILED.*Failure.*%s.*", REVISION, BT_NAME_ESCAPED_REGEXP));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    myGson = new Gson();
    setExpectedApiPath("/api/http");
    setExpectedEndpointPrefix("/projects/key:" + OWNER + "/repositories/" + CORRECT_REPO);
    super.setUp();
    myProjectFeatureId = myProject.addFeature(OAuthConstants.FEATURE_TYPE, new HashMap<String, String>() {{
      put(SpaceOAuthKeys.SPACE_CLIENT_ID, FAKE_CLIENT_ID);
      put(SpaceOAuthKeys.SPACE_CLIENT_SECRET, FAKE_CLIENT_SECRET);
      put(Constants.SPACE_SERVER_URL, getServerUrl());
    }}).getId();
    myPublisherSettings = new SpaceSettings(myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider, myOAuthConnectionsManager, myOAuthTokenStorage);
    Map<String, String> params = getPublisherParams();
    SpaceConnectDescriber connector = SpaceUtils.getConnectionData(params, myOAuthConnectionsManager, myBuildType.getProject());
    myPublisher = new SpacePublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems, connector);
  }

  /*
    The following three tests are disabled because these are not applicable to JetBrains Space test connection scenarios.
    TODO: Another tests are required for Space publisher.
   */
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

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.SPACE_CREDENTIALS_TYPE, Constants.SPACE_CREDENTIALS_CONNECTION);
      put(Constants.SPACE_CONNECTION_ID, myProjectFeatureId);
      put(Constants.SPACE_PROJECT_KEY, OWNER);
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    respondWithError(httpResponse, 404, String.format("Unexpected GET request to URL: %s", url));
    return false;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    if (url.endsWith(SpaceToken.JWT_TOKEN_ENDPOINT)) {
      String expectedTokenRequest = String.format("%s=%s&%s=%s", SpaceToken.GRANT_TYPE, SpaceToken.CLIENT_CREDENTIALS_GRAND_TYPE, SpaceToken.SCOPE, SpaceToken.ALL_SCOPE);
      if (requestData.equals(expectedTokenRequest)) {
        Map<String, String> tokenResponseMap = new HashMap<String, String>();
        tokenResponseMap.put(TOKEN_TYPE_FIELD_NAME, "fake_token");
        tokenResponseMap.put(ACCESS_TOKEN_FIELD_NAME, "mytoken");
        String jsonResponse = myGson.toJson(tokenResponseMap);
        httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
        return true;
      }
      respondWithError(httpResponse, 500, String.format("Unexpected token request payload: %s", requestData));
      return false;
    } else if (url.endsWith("check-service")) {
      if (!url.contains("projects/key:owner")) {
        respondWithError(httpResponse, 404, String.format("Unexpected check-service URL: %s", url));
      }
      return true;
    }

    return isUrlExpected(url, httpResponse);
  }
}