

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import java.util.*;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudBuildStatuses;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudCommitBuildStatus;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudRepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.pullRequests.VcsAuthType;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jmock.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class BitbucketCloudPublisherTest extends HttpPublisherTest {

  private final Map<String, List<BitbucketCloudCommitBuildStatus>> myRevisionToStatus = new HashMap<>();
  private final BitbucketCloudBuildNameProvider myBuildNameProvider = new BitbucketCloudBuildNameProvider();

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

  public void test_vcs_root_auth_with_username_only_in_url_provides_correct_credentials() throws Exception {
    SVcsRoot vcsRoot = myFixture.addVcsRoot("jetbrains.git", "", myBuildType);
    vcsRoot.setProperties(new HashMap<String, String>() {{
        put(Constants.GIT_URL_PARAMETER, "http://user@localhost/" + OWNER + "/" + CORRECT_REPO);
        put(Constants.VCS_AUTH_METHOD, VcsAuthType.PASSWORD.toString());
        put("secure:password", "pwd");
      }});
    HttpCredentials credentials = myPublisherSettings.getCredentials(myBuildType.getProject(), vcsRoot, new HashMap<String, String>() {{
      put(Constants.AUTH_TYPE, Constants.AUTH_TYPE_VCS);
    }});
    then(credentials).isExactlyInstanceOf(UsernamePasswordCredentials.class)
                     .isEqualTo(new UsernamePasswordCredentials("user", "pwd"));
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
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.STOPPED.name(), null, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.STOPPED.name(), null, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.SUCCESSFUL.name(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                 null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                 null, "", null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                null, DefaultStatusMessages.BUILD_QUEUED, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new BitbucketCloudCommitBuildStatus(null, BitbucketCloudBuildStatus.INPROGRESS.name(),
                                                                                                                                 null, DefaultStatusMessages.BUILD_STARTED, null)).getTriggeredEvent());
  }

  public void should_allow_queued_depending_on_build_type() {
    Mock removedBuildMock = new Mock(BuildPromotionEx.class);
    removedBuildMock.stubs().method("getBuildTypeId").withNoArguments().will(returnValue("buildType"));
    BuildPromotion removedBuild = (BuildPromotion)removedBuildMock.proxy();
    BitbucketCloudPublisher publisher = (BitbucketCloudPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatus(removedBuild, new BitbucketCloudCommitBuildStatus("buildType", BitbucketCloudBuildStatus.INPROGRESS.name(), null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=123")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
    assertFalse(publisher.getRevisionStatus(removedBuild, new BitbucketCloudCommitBuildStatus("anotherBuildType", BitbucketCloudBuildStatus.INPROGRESS.name(), null, DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=321")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
  }

  public void default_build_name_test() {
    assertEquals("My Default Test Project / My Default Test Build Type", myPublisherSettings.getDefaultBuildName(myBuildType));
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/statuses/build/")) {
      responsWithStatus(httpResponse);
    } else if (url.contains("/statuses")) {
      respondWithStatuses(url, httpResponse);
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

  private void respondWithStatuses(String url, HttpResponse httpResponse) {
    String revision = getRevision(url, "/2.0/repositories/owner/project/commit/");
    List<BitbucketCloudCommitBuildStatus> statusesList = myRevisionToStatus.getOrDefault(revision, Collections.emptyList());
    BitbucketCloudBuildStatuses statuses = new BitbucketCloudBuildStatuses(statusesList, statusesList.size(), 1, statusesList.size());
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
    BitbucketCloudCommitBuildStatus status = gson.fromJson(requestData, BitbucketCloudCommitBuildStatus.class);
    String revision = getRevision(url, "/2.0/repositories/owner/project/commit/");
    if (revision != null) {
      myRevisionToStatus.computeIfAbsent(revision, k -> new ArrayList<>()).add(status);
    }
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

  private void addExpectation(EventToTest event, String expectedStatus, String expectedStatusMessage) {
    myExpectedRegExps.put(event, String.format(".*/2.0/repositories/owner/project/commit/%s.*ENTITY:.*\"key\":\"%s\".*%s.*%s.*",
                                                            REVISION, myBuildType.getBuildTypeId(), expectedStatus, expectedStatusMessage));

  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/2.0");
    setExpectedEndpointPrefix("/repositories/" + OWNER + "/" + CORRECT_REPO);
    super.setUp();
    myExpectedRegExps.clear();
    addExpectation(EventToTest.QUEUED, "INPROGRESS", DefaultStatusMessages.BUILD_QUEUED);
    addExpectation(EventToTest.REMOVED, "STOPPED", DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE);
    addExpectation(EventToTest.STARTED, "INPROGRESS", DefaultStatusMessages.BUILD_STARTED);
    addExpectation(EventToTest.FINISHED, "SUCCESSFUL", "Success");
    addExpectation(EventToTest.FAILED, "FAILED", "Failure");
    addExpectation(EventToTest.COMMENTED_SUCCESS, "SUCCESSFUL", String.format("Success with a comment by %s:.*%s.*", USER.toLowerCase(), COMMENT));
    addExpectation(EventToTest.COMMENTED_FAILED, "FAILED", String.format("Failure with a comment by %s:.*%s.*", USER.toLowerCase(), COMMENT));
    addExpectation(EventToTest.COMMENTED_INPROGRESS, "INPROGRESS", String.format("Running with a comment by %s:.*%s.*", USER.toLowerCase(), COMMENT));
    addExpectation(EventToTest.COMMENTED_INPROGRESS_FAILED, "FAILED", String.format("%s.*with a comment by %s:.*%s.*", PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    addExpectation(EventToTest.INTERRUPTED, "STOPPED", PROBLEM_DESCR);
    addExpectation(EventToTest.FAILURE_DETECTED, "FAILED", PROBLEM_DESCR);
    addExpectation(EventToTest.MARKED_SUCCESSFUL, "SUCCESSFUL", DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    addExpectation(EventToTest.MARKED_RUNNING_SUCCESSFUL, "INPROGRESS", DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    addExpectation(EventToTest.PAYLOAD_ESCAPED, "FAILED", BT_NAME_ESCAPED_REGEXP + ".*Failure");
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*2.0/repositories/owner/project.*");

    String apiUrl = getServerUrl() + "/";
    Map<String, String> params = getPublisherParams();
    BitbucketCloudSettings publisherSettings = new BitbucketCloudSettings(
       new MockPluginDescriptor(),
       myWebLinks,
       myProblems,
       myTrustStoreProvider,
       myOAuthConnectionsManager,
       myOAuthTokenStorage,
       myFixture.getUserModel(),
       myFixture.getSecurityContext(),
       myFixture.getProjectManager(),
       myBuildNameProvider
    );
    BitbucketCloudSettings publisherSettingsSpy = spy(publisherSettings);
    doReturn(apiUrl).when(publisherSettingsSpy).getDefaultApiUrl();
    myPublisherSettings = publisherSettingsSpy;

    BitbucketCloudPublisher publisher = new BitbucketCloudPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myBuildNameProvider);
    BitbucketCloudPublisher publisherSpy = spy(publisher);
    doReturn(apiUrl).when(publisherSpy).getBaseUrl();
    myPublisher = publisherSpy;

    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));
    myRevisionToStatus.clear();

    doReturn(myPublisher).when(myPublisherSettings).createPublisher(any(), any(), anyMap());
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