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

package jetbrains.buildServer.commitPublisher.github;

import com.google.common.collect.Lists;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.github.api.GitHubChangeState;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapperImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.CombinedCommitStatus;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.CommitStatus;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.Permissions;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.RepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GitHubPublisherTest extends HttpPublisherTest {

  private ChangeStatusUpdater myChangeStatusUpdater;

  public GitHubPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*description\":\"%s\".*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*%s\".*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*%s.*", REVISION, DefaultStatusMessages.BUILD_STARTED));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*success.*%s.*", REVISION, DefaultStatusMessages.BUILD_FINISHED));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*%s.*", REVISION, DefaultStatusMessages.BUILD_FAILED));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*%s.*", REVISION, DefaultStatusMessages.BUILD_FAILED)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*success.*%s.*", REVISION, DefaultStatusMessages.BUILD_FINISHED)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*%s.*", REVISION, DefaultStatusMessages.BUILD_STARTED)); // not to be tested
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*%s.*%s.*", REVISION, DefaultStatusMessages.BUILD_FAILED, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, String.format(".*/repos/owner/project .*")); // not to be tested
  }

  @TestFor(issues="TW-54352")
  public void default_context_must_not_contains_long_unicodes() {
    char[] btNameCharCodes = { 0x41, 0x200d, 0x42b, 0x20, 0x3042, 0x231a, 0xd83e, 0xdd20, 0x39, 0xfe0f, 0x20e3, 0xd83d, 0x20, 0xdee9, 0xfe0f };
    myBuildType.setName(new String(btNameCharCodes));
    char[] prjNameCharCodes =  { 0x45, 0x263A,  0x09, 0xd841, 0xdd20 };
    myBuildType.getProject().setName(new String(prjNameCharCodes));
    SBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    String context = ((GitHubPublisher) myPublisher).getDefaultContext(build.getBuildPromotion());
    char[] expectedBTNameCharCodes = { 0x41, 0x42b, 0x20, 0x3042, 0x231a, 0x39};
    char[] expectedPrjNameCharCodes =  { 0x45, 0x263A };
    then(context).isEqualTo(new String(expectedBTNameCharCodes) + " (" + new String(expectedPrjNameCharCodes) + ")");
  }

  public void test_buildFinishedSuccessfully_server_url_with_subdir() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v3");
    params.put(Constants.GITHUB_SERVER, getServerUrl() + "/subdir/api/v3");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems, myWebLinks, new CommitStatusesCache<>());
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v3");
    params.put(Constants.GITHUB_SERVER, getServerUrl() + "/subdir/api/v3/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems, myWebLinks, new CommitStatusesCache<>());
    test_buildFinished_Successfully();
  }


  public void should_fail_with_error_on_wrong_vcs_url() {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch (PublisherException ex) {
      then(ex.getMessage()).matches("Cannot parse.*" + myVcsRoot.getName() + ".*");
    }
  }

  public void should_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    GitHubPublisher publisher = (GitHubPublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (CommitStatus)null));
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus(null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus("nonsense", null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Error.getState(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Success.getState(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Failure.getState(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Pending.getState(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Pending.getState(), null, "nonsense", null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Pending.getState(), null, DefaultStatusMessages.BUILD_STARTED, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Pending.getState(), null, DefaultStatusMessages.BUILD_QUEUED, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new CommitStatus(GitHubChangeState.Pending.getState(), null, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null)).getTriggeredEvent());
  }

  public void should_allow_queued_depending_on_build_type() {
    Mock removedBuildMock = new Mock(SQueuedBuild.class);
    removedBuildMock.stubs().method("getBuildTypeId").withNoArguments().will(returnValue("buildType"));
    removedBuildMock.stubs().method("getItemId").withNoArguments().will(returnValue("123"));
    Mock buildPromotionMock = new Mock(BuildPromotion.class);
    buildPromotionMock.stubs().method("getBuildTypeExternalId").withNoArguments().will(returnValue("buildTypeExtenalId"));
    buildPromotionMock.stubs().method("getAssociatedBuild").withNoArguments().will(returnValue(null));
    Mock buildTypeMock = new Mock(SBuildType.class);
    buildTypeMock.stubs().method("getName").withNoArguments().will(returnValue("buildName"));
    Mock projectMock = new Mock(SProject.class);
    projectMock.stubs().method("getName").withNoArguments().will(returnValue("projectName"));
    buildTypeMock.stubs().method("getProject").withNoArguments().will(returnValue(projectMock.proxy()));
    buildPromotionMock.stubs().method("getBuildType").withNoArguments().will(returnValue(buildTypeMock.proxy()));
    removedBuildMock.stubs().method("getBuildPromotion").withNoArguments().will(returnValue(buildPromotionMock.proxy()));
    SQueuedBuild removedBuild = (SQueuedBuild)removedBuildMock.proxy();

    GitHubPublisher publisher = (GitHubPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new CommitStatus(GitHubChangeState.Pending.getState(), "http://localhost:8111/viewQueued.html?itemId=123", DefaultStatusMessages.BUILD_QUEUED, "buildName (projectName)")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new CommitStatus(GitHubChangeState.Pending.getState(), "http://localhost:8111/viewQueued.html?itemId=321", DefaultStatusMessages.BUILD_QUEUED, "custom context")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
  }

  public void should_use_build_name_as_context() throws Exception {
    final String expectedContext = "My Default Test Build Type (My Default Test Project)";
    GitHubPublisher publisher = (GitHubPublisher)myPublisher;
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    String buildContext = publisher.getBuildContext(queuedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildContext);

    RunningBuildEx startedBuild = myFixture.flushQueueAndWait();
    buildContext = publisher.getBuildContext(startedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildContext);
  }

  public void should_use_context_from_parameters() throws Exception {
    final String expectedContext = "My custom context name";
    myBuildType.addBuildParameter(new SimpleParameter(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM, expectedContext));
    GitHubPublisher publisher = (GitHubPublisher)myPublisher;
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    String buildContext = publisher.getBuildContext(queuedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildContext);

    RunningBuildEx startedBuild = myFixture.flushQueueAndWait();
    buildContext = publisher.getBuildContext(startedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildContext);
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("");
    setExpectedEndpointPrefix("/repos/" + OWNER + "/" + CORRECT_REPO);
    super.setUp();

    Map<String, String> params = getPublisherParams();
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));

    myChangeStatusUpdater = new ChangeStatusUpdater(new GitHubApiFactoryImpl(new HttpClientWrapperImpl(new HTTPRequestBuilder.ApacheClient43RequestHandler(), () -> null),
                                                                             myFixture.getSingletonService(OAuthTokensStorage.class),
                                                                             myFixture.getSingletonService(OAuthConnectionsManager.class),
                                                                             myFixture.getProjectManager()), myFixture.getVcsHistory());

    myPublisherSettings = new GitHubSettings(myChangeStatusUpdater, new MockPluginDescriptor(), myWebLinks, myProblems,
                                             myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext(),
                                             myTrustStoreProvider);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems, myWebLinks, new CommitStatusesCache<>());
  }

  @Override
  protected void setPublisherTimeout(int timeout) {
    super.setPublisherTimeout(timeout);
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {
        @NotNull
        @Override
        public Map<String, String> getUserDefinedProperties() {
          return Collections.singletonMap("teamcity.github.http.timeout", String.valueOf(timeout));
        }
      });
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/repos" +  "/" + OWNER + "/" + CORRECT_REPO + "/commits")) {
      respondWithCommitsInfo(httpResponse, "My Default Test Build Type (My Default Test Project)");
    } else if (url.contains("/repos" +  "/" + OWNER + "/" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
    } else if (url.contains("/repos"  + "/" + OWNER + "/" +  READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  private void respondWithCommitsInfo(@NotNull HttpResponse httpResponse, String context) {
    CombinedCommitStatus status = new CombinedCommitStatus();
    status.total_count = 1;
    status.statuses = Lists.newArrayList(
      new CommitStatus(GitHubChangeState.Pending.getState(), "url", DefaultStatusMessages.BUILD_QUEUED, context)
    );
    String jsonResponse = gson.toJson(status);
    httpResponse.setEntity(new StringEntity(jsonResponse, StandardCharsets.UTF_8));
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    RepoInfo repoInfo = new RepoInfo();
    repoInfo.name = repoName;
    repoInfo.permissions = new Permissions();
    repoInfo.permissions.pull = true;
    repoInfo.permissions.push = isPushPermitted;
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.GITHUB_USERNAME, "user");
      put(Constants.GITHUB_PASSWORD, "pwd");
      put(Constants.GITHUB_SERVER, getServerUrl());
    }};
  }
}