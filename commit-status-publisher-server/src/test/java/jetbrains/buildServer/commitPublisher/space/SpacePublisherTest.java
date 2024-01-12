

package jetbrains.buildServer.commitPublisher.space;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.space.data.SpaceBuildStatusInfo;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.oauth.OAuthConstants;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthKeys;
import jetbrains.buildServer.serverSide.oauth.space.application.SpaceApplicationInformationManager;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.commitPublisher.space.SpaceToken.ACCESS_TOKEN_FIELD_NAME;
import static jetbrains.buildServer.commitPublisher.space.SpaceToken.TOKEN_TYPE_FIELD_NAME;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class SpacePublisherTest extends HttpPublisherTest {

  private Map<String, List<SpaceBuildStatusInfo>> myRevisionToStatus = new HashMap<>();

  protected static final String FAKE_CLIENT_ID = "clientid";
  protected static final String FAKE_CLIENT_SECRET = "clientsecret";
  protected String myProjectFeatureId;

  public SpacePublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SCHEDULED.*%s.*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*TERMINATED.*%s\".*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*RUNNING.*%s.*", REVISION, DefaultStatusMessages.BUILD_STARTED));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SUCCEEDED.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*TERMINATED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*FAILING.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SUCCEEDED.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*RUNNING.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*/projects/key:owner/commit-statuses/check-service.*");
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*FAILED.*Failure.*%s.*", REVISION, BT_NAME_ESCAPED_REGEXP));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/api/http");
    setExpectedEndpointPrefix("/projects/key:" + OWNER + "/repositories/" + CORRECT_REPO);
    super.setUp();
    myProjectFeatureId = myProject.addFeature(OAuthConstants.FEATURE_TYPE, new HashMap<String, String>() {{
      put(SpaceOAuthKeys.SPACE_CLIENT_ID, FAKE_CLIENT_ID);
      put(SpaceOAuthKeys.SPACE_CLIENT_SECRET, FAKE_CLIENT_SECRET);
      put(Constants.SPACE_SERVER_URL, getServerUrl());
    }}).getId();
    final SpaceApplicationInformationManager applicationInformationManager = Mockito.mock(SpaceApplicationInformationManager.class);
    myPublisherSettings = new SpaceSettings(new MockPluginDescriptor(),
                                            myWebLinks,
                                            myProblems,
                                            myTrustStoreProvider,
                                            myOAuthConnectionsManager,
                                            myFixture.getSecurityContext(),
                                            applicationInformationManager);
    Map<String, String> params = getPublisherParams();
    SpaceConnectDescriber connector = SpaceUtils.getConnectionData(params, myOAuthConnectionsManager, myBuildType.getProject());
    myPublisher = new SpacePublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, connector, new CommitStatusesCache<>());
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));
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
      put(Constants.SPACE_SERVER_URL, getServerUrl());
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.endsWith("commit-statuses")) {
      String revision = getRevision(url, "/api/http/projects/key:owner/repositories/project/revisions/");
      responseWithCommitStatuses(httpResponse, revision);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected GET request to URL: %s", url));
      return false;
    }
    return true;
  }

  private void responseWithCommitStatuses(HttpResponse httpResponse, String revision) {
    List<SpaceBuildStatusInfo> statuses = myRevisionToStatus.getOrDefault(revision, Collections.emptyList());
    String json = gson.toJson(statuses);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    if (url.endsWith(SpaceToken.JWT_TOKEN_ENDPOINT)) {
      String expectedTokenRequest = String.format("%s=%s&%s=%s", SpaceToken.GRANT_TYPE, SpaceToken.CLIENT_CREDENTIALS_GRAND_TYPE, SpaceToken.SCOPE, SpaceToken.ALL_SCOPE);
      if (requestData.equals(expectedTokenRequest)) {
        Map<String, String> tokenResponseMap = new HashMap<String, String>();
        tokenResponseMap.put(TOKEN_TYPE_FIELD_NAME, "fake_token");
        tokenResponseMap.put(ACCESS_TOKEN_FIELD_NAME, "mytoken");
        String jsonResponse = gson.toJson(tokenResponseMap);
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
    } else if (url.endsWith("commit-statuses")) {
      String revision = getRevision(url, "/api/http/projects/key:owner/repositories/project/revisions/");
      SpaceBuildStatusInfo status = gson.fromJson(requestData, SpaceBuildStatusInfo.class);
      myRevisionToStatus.computeIfAbsent(revision, k -> new ArrayList<>()).add(status);
    }

    return isUrlExpected(url, httpResponse);
  }

  public void shoudld_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    SpacePublisher publisher = (SpacePublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (SpaceBuildStatusInfo)null));
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(null, null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo("nonsense", null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.FAILED.getName(), null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.FAILING.getName(), null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.TERMINATED.getName(), null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SUCCEEDED.getName(), null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), "", null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), DefaultStatusMessages.BUILD_STARTED, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), null, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), DefaultStatusMessages.BUILD_QUEUED, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.TERMINATED.getName(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.TERMINATED.getName(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null, null, null, "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).getTriggeredEvent());
  }

  public void should_allow_queued_depending_on_build_type() {
    Mock removedBuildMock = new Mock(SQueuedBuild.class);
    removedBuildMock.stubs().method("getBuildTypeId").withNoArguments().will(returnValue("buildType"));
    removedBuildMock.stubs().method("getItemId").withNoArguments().will(returnValue("123"));
    Mock buildPromotionMock = new Mock(BuildPromotion.class);
    buildPromotionMock.stubs().method("getBuildTypeExternalId").withNoArguments().will(returnValue("buildTypeExtenalId"));
    removedBuildMock.stubs().method("getBuildPromotion").withNoArguments().will(returnValue(buildPromotionMock.proxy()));
    SQueuedBuild removedBuild = (SQueuedBuild)removedBuildMock.proxy();

    SpacePublisher publisher = (SpacePublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), null, null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=123", "buildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), null, null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=321", "anotherBuildTypeExtenalId", Constants.SPACE_DEFAULT_DISPLAY_NAME, "buildPromotionId")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }

  protected boolean requiresAuthPreRequest() {
    return true;
  }

  @Override
  protected boolean checkEventFinished(@NotNull String requestString, boolean isSuccessful) {
    return requestString.contains(isSuccessful ? "SUCCEEDED" : "FAILING");
  }
}