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

package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudBuildStatuses;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudCommitBuildStatus;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudRepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jmock.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class BitbucketCloudPublisherTest extends HttpPublisherTest {

  public BitbucketCloudPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*INPROGRESS.*%s.*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*STOPPED.*%s\".*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*INPROGRESS.*%s.*", REVISION, DefaultStatusMessages.BUILD_STARTED));
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
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*SUCCESSFUL.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*INPROGRESS.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*2.0/repositories/owner/project.*");
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*FAILED.*%s.*Failure.*", REVISION, BT_NAME_ESCAPED_REGEXP));
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
    then(getRequestAsString()).isNotNull().matches(myExpectedRegExps.get(EventToTest.TEST_CONNECTION));
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

  public void shoudld_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    BitbucketCloudPublisher publisher = (BitbucketCloudPublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (BitbucketCloudCommitBuildStatus)null));
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, "nonsense", null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.FAILED.name(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.STOPPED.name(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.SUCCESSFUL.name(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                 null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                 null, "", null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                null, DefaultStatusMessages.BUILD_QUEUED, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null,
                                                                                                                                            BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                            null,
                                                                                                                                            DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE,
                                                                                                                                            null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                 null, DefaultStatusMessages.BUILD_STARTED, null)).getTriggeredEvent());
  }

  public void should_allow_queued_depending_on_build_type() {
    Mock removedBuildMock = new Mock(SQueuedBuild.class);
    removedBuildMock.stubs().method("getBuildTypeId").withNoArguments().will(returnValue("buildType"));
    removedBuildMock.stubs().method("getItemId").withNoArguments().will(returnValue("123"));
    SQueuedBuild removedBuild = (SQueuedBuild)removedBuildMock.proxy();
    BitbucketCloudPublisher publisher = (BitbucketCloudPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new BitbucketCloudCommitBuildStatus("buildType", BitbucketCloudBuildStatus.INPROGRESS.name(), null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=123")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new BitbucketCloudCommitBuildStatus("anotherBuildType", BitbucketCloudBuildStatus.INPROGRESS.name(), null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=321")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/statuses/build/")) {
      responsWithStatus(httpResponse);
    } else if (url.contains("/statuses")) {
      responsWithStatuses(httpResponse);
    }  else if (url.endsWith("/repositories/" + OWNER + "/" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
    } else if (url.endsWith("/repositories/" + OWNER + "/" + READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  private void responsWithStatuses(HttpResponse httpResponse) {
    BitbucketCloudCommitBuildStatus status = new BitbucketCloudCommitBuildStatus("", BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                 "My Default Test Project / My Default Test Build Type", DefaultStatusMessages.BUILD_QUEUED, "");
    BitbucketCloudBuildStatuses statuses = new BitbucketCloudBuildStatuses(Collections.singleton(status), 1 ,1, 1);
    String json = gson.toJson(statuses);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  private void responsWithStatus(HttpResponse httpResponse) {
    BitbucketCloudCommitBuildStatus status = new BitbucketCloudCommitBuildStatus("", BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                 "My Default Test Project / My Default Test Build Type", DefaultStatusMessages.BUILD_QUEUED, "");
    String json = gson.toJson(status);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }


  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    BitbucketCloudRepoInfo repoInfo = new BitbucketCloudRepoInfo();
    repoInfo.slug = repoName;
    repoInfo.type = "repository";
    repoInfo.is_private = true;
    repoInfo.description = "";
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, StandardCharsets.UTF_8));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/2.0");
    setExpectedEndpointPrefix("/repositories/" + OWNER + "/" + CORRECT_REPO);
    super.setUp();
    Map<String, String> params = getPublisherParams();
    myPublisherSettings = new BitbucketCloudSettings(new MockPluginDescriptor(),
                                                     myWebLinks,
                                                     myProblems,
                                                     myTrustStoreProvider,
                                                     myOAuthConnectionsManager,
                                                     myOAuthTokenStorage,
                                                     myFixture.getUserModel(),
                                                     myFixture.getSecurityContext(),
                                                     myFixture.getProjectManager());
    BitbucketCloudPublisher publisher = new BitbucketCloudPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    publisher.setBaseUrl(getServerUrl() + "/");
    ((BitbucketCloudSettings)myPublisherSettings).setDefaultApiUrl(getServerUrl() + "/");
    myPublisher = publisher;
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.BITBUCKET_CLOUD_USERNAME, "user");
      put(Constants.BITBUCKET_CLOUD_PASSWORD, "pwd");
    }};
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }
}