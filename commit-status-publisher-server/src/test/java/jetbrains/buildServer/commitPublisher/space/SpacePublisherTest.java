/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.space;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.space.data.SpaceBuildStatusInfo;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SimpleParameter;
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

  public SpacePublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SCHEDULED.*%s.*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*/projects/key:owner/repositories/project/revisions/%s/commit-statuses.*ENTITY:.*SCHEDULED.*%s\".*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE));
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
    myPublisherSettings = new SpaceSettings(new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider, myOAuthConnectionsManager, myOAuthTokenStorage);
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
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.endsWith("commit-statuses")) {
      responseWithCommitStatuses(httpResponse);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected GET request to URL: %s", url));
      return false;
    }
    return true;
  }

  private void responseWithCommitStatuses(HttpResponse httpResponse) {
    SpaceBuildStatusInfo status =
      new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), DefaultStatusMessages.BUILD_QUEUED, System.currentTimeMillis(), "My Default Test Project / My Default Test Build Type", "");
    String json = gson.toJson(Collections.singleton(status));
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
    }

    return isUrlExpected(url, httpResponse);
  }

  public void shoudld_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    SpacePublisher publisher = (SpacePublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (SpaceBuildStatusInfo)null));
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(null, null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo("nonsense", null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.FAILED.getName(), null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.FAILING.getName(), null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.TERMINATED.getName(), null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SUCCEEDED.getName(), null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), "", null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), null, null, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.RUNNING.getName(), DefaultStatusMessages.BUILD_STARTED, null, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), null, null, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), DefaultStatusMessages.BUILD_QUEUED, null, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), "", null, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null, null, null)).getTriggeredEvent());
  }

  public void should_define_correctly_if_event_allowed() {
    MockQueuedBuild removedBuild = new MockQueuedBuild();
    removedBuild.setBuildTypeId("buildType");
    removedBuild.setItemId("123");
    SpacePublisher publisher = (SpacePublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), null, null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost/viewQueued.html?itemId=123")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new SpaceBuildStatusInfo(SpaceBuildStatus.SCHEDULED.getName(), null, null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost/viewQueued.html?itemId=321")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }

  protected boolean requiresAuthPreRequest() {
    return true;
  }
}