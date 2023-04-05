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

package jetbrains.buildServer.commitPublisher.gitlab;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabAccessLevel;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabPermissions;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabReceiveCommitStatus;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabRepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
import org.apache.commons.io.IOUtils;
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
public class GitlabPublisherTest extends HttpPublisherTest {

  private static final String GROUP_REPO = "group_repo";
  private static final String MERGE_RESULT_COMMIT = "31337";

  public GitlabPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*pending.*%s.*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*canceled.*%s\".*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*running.*%s.*", REVISION, DefaultStatusMessages.BUILD_STARTED));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*success.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*failed.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*canceled.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*failed.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*success.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*running.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*/projects/owner%2Fproject .*");
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*failed.*%s.*Failure.*", REVISION, BT_NAME_ESCAPED_REGEXP));
  }

  public void test_buildFinishedSuccessfully_server_url_with_subdir() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v4");
    params.put(Constants.GITLAB_API_URL, getServerUrl() + "/subdir/api/v4");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v4");
    params.put(Constants.GITLAB_API_URL, getServerUrl() + "/subdir/api/v4/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    test_buildFinished_Successfully();
  }

  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getMessage()).matches("Cannot parse.*" + myVcsRoot.getName() + ".*");
    }
  }

  public void should_work_with_dots_in_id() throws PublisherException, InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/own.er/pro.ject"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    setExpectedEndpointPrefix("/projects/own%2Eer%2Fpro%2Eject");
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(String.format(".*/projects/own%%2Eer%%2Fpro%%2Eject/statuses/%s.*ENTITY:.*success.*Success.*", REVISION));
  }

  public void should_work_with_slashes_in_id() throws PublisherException, InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/group/subgroup/anothergroup/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    setExpectedEndpointPrefix("/projects/group%2Fsubgroup%2Fanothergroup%2Fproject");
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(String.format(".*/projects/group%%2Fsubgroup%%2Fanothergroup%%2Fproject/statuses/%s.*ENTITY:.*success.*Success.*", REVISION));
  }

  public void test_testConnection_group_repo() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", getServerUrl()  + "/" + OWNER + "/" + GROUP_REPO));
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
    then(getRequestAsString()).isNotNull()
                          .doesNotMatch(".*error.*")
                          .matches(".*/projects/owner%2Fgroup_repo .*");
  }

  public void shoudld_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    GitlabPublisher publisher = (GitlabPublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (GitLabReceiveCommitStatus)null));
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, "nonsense", null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.SUCCESS.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.FAILED.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.RUNNING.getName(), DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.CANCELED.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.CANCELED.getName(), "nonsense", null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.RUNNING.getName(), null, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.RUNNING.getName(), DefaultStatusMessages.BUILD_STARTED, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.CANCELED.getName(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), "", null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, null, null)).getTriggeredEvent());
  }

  public void should_allow_queued_depending_on_build_type() {
    Mock removedBuildMock = new Mock(SQueuedBuild.class);
    removedBuildMock.stubs().method("getBuildTypeId").withNoArguments().will(returnValue("buildType"));
    removedBuildMock.stubs().method("getItemId").withNoArguments().will(returnValue("123"));
    Mock buildPromotionMock = new Mock(BuildPromotion.class);
    Mock buildTypeMock = new Mock(SBuildType.class);
    buildTypeMock.stubs().method("getFullName").withNoArguments().will(returnValue("typeFullName"));
    buildPromotionMock.stubs().method("getBuildType").withNoArguments().will(returnValue(buildTypeMock.proxy()));
    removedBuildMock.stubs().method("getBuildPromotion").withNoArguments().will(returnValue(buildPromotionMock.proxy()));
    SQueuedBuild removedBuild = (SQueuedBuild)removedBuildMock.proxy();

    GitlabPublisher publisher = (GitlabPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, "typeFullName", "http://localhost:8111/viewQueued.html?itemId=123")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, "anotherTypeFullName", "http://localhost:8111/viewQueued.html?itemId=321")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
  }

  public void buildFinishedSuccessfully_on_merge_result_ref() throws Exception {
    final String mergeResultRevision = MERGE_RESULT_COMMIT;
    final String mergeResultRef = "refs/merge-requests/1/merge";
    final Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/api/v4");
    params.put(Constants.GITLAB_API_URL, getServerUrl() + "/api/v4");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/owner/project"));
    final VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    final VcsRootInstanceEntry rootEntry = new VcsRootInstanceEntry(vcsRootInstance, CheckoutRules.createOn(""));
    final RepositoryVersion repositoryVersion = new RepositoryVersion(mergeResultRevision, mergeResultRevision, mergeResultRef);
    myRevision = new BuildRevision(rootEntry, repositoryVersion);
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());

    test_buildFinished_Successfully();
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/api/v4");
    setExpectedEndpointPrefix("/projects/" + OWNER + "%2F" + CORRECT_REPO);
    super.setUp();
    myPublisherSettings = new GitlabSettings(new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider);
    Map<String, String> params = getPublisherParams();
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));
  }

  @Override
  @Test(enabled=false)
  // the test is disabled for GitLab due to decoding of %2F in URLs in Location header by http client
  // which results in the redirected request containing slash instead of %2F
  public void test_redirection(int httpCode) throws Exception {
    super.test_redirection(httpCode);
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.GITLAB_TOKEN, "TOKEN");
      put(Constants.GITLAB_API_URL, getServerUrl() + getExpectedApiPath());
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/projects/" + OWNER + "%2F" + CORRECT_REPO + "/repository/commits")) {
      if (url.endsWith("/" + MERGE_RESULT_COMMIT)) {    // /api/v4/projects/test%2Fspring-template/repository/commits/5b01037812d17f741d94e2c8819cb8775e9cce6d
        respondWithMergeResultCommit(httpResponse);
      } else if (url.endsWith("/refs")) {               // /api/v4/projects/test%2Fspring-template/repository/commits/4071e1f3606ea74964146499a165b163b5f11821/refs
        final String[] tokens = url.split("/");
        respondWithCommitRefs(httpResponse, tokens[tokens.length - 2]);
      } else {
        respondWithCommits(httpResponse, url.substring(url.lastIndexOf('=') + 1).replace('+', ' '));
      }
    } else if (url.contains("/projects/" + OWNER + "%2F" + CORRECT_REPO + "/merge_requests")) {
      respondWithMergeRequest(httpResponse);
    } else if (url.contains("/projects" +  "/" + OWNER + "%2F" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, false, true);
    } else if (url.contains("/projects"  + "/" + OWNER + "%2F" +  GROUP_REPO)) {
      respondWithRepoInfo(httpResponse, GROUP_REPO, true, true);
    } else if (url.contains("/projects"  + "/" + OWNER + "%2F" +  READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false, false);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  private void respondWithCommits(HttpResponse httpResponse, String buildName) {
    String decodedName;
    try {
      decodedName = URLDecoder.decode(buildName, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      decodedName = buildName;
    }
    ArrayList<GitLabReceiveCommitStatus> statuses = new ArrayList<>();
    statuses.add(new GitLabReceiveCommitStatus(1L, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, decodedName, ""));
    String json = gson.toJson(statuses);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  private void respondWithMergeRequest(HttpResponse httpResponse) {
    respondFromResource(httpResponse, "/gitlab/mergeRequest.json");
  }

  private void respondWithMergeResultCommit(HttpResponse httpResponse) {
    respondFromResource(httpResponse, "/gitlab/mergeResultCommit.json");
  }

  private void respondWithCommitRefs(HttpResponse httpResponse, String commit) {
    respondFromResource(httpResponse, "/gitlab/refs_" + commit + ".json");
  }

  private void respondFromResource(HttpResponse httpResponse, String resourceName) {
    try (final InputStream in = getClass().getResourceAsStream(resourceName)) {
      if (in == null) {
        fail("response json resource not found " + resourceName);
      }
      final String body = IOUtils.toString(in);
      httpResponse.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }


  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isGroupRepo, boolean isPushPermitted) {
    GitLabRepoInfo repoInfo = new GitLabRepoInfo();
    repoInfo.id = "111";
    repoInfo.permissions = new GitLabPermissions();
    GitLabAccessLevel accessLevel = new GitLabAccessLevel();
    accessLevel.access_level = isPushPermitted ? 30 : 20;
    if (isGroupRepo) {
      repoInfo.permissions.group_access = accessLevel;
    } else {
      repoInfo.permissions.project_access = accessLevel;
    }
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

}