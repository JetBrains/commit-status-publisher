

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

package jetbrains.buildServer.commitPublisher.gitlab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitlab.data.*;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.PipelineInfo;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsModificationHistoryEx;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GitlabPublisherTest extends HttpPublisherTest {

  private static final String GROUP_REPO = "group_repo";
  private static final String TRANSITIVE_REPO = "transitive_repo";
  protected final static String TRANSITIVE_REPO_DUPLICATE = TRANSITIVE_REPO + "_duplicate";
  protected final static String TRANSITIVE_REPO_CORRECT = TRANSITIVE_REPO + "_correct";
  protected final static String TRANSITIVE_REPO_EMPTY = TRANSITIVE_REPO + "_empty";
  private static final String MERGE_RESULT_COMMIT = "31337";
  private final Map<String, List<GitLabPublishCommitStatus>> myRevisionToStatuses = new HashMap<>();
  private final GitLabBuildNameProvider myBuildNameProvider = new GitLabBuildNameProvider();

  private VcsModificationHistoryEx myVcsModificationHistory;

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
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v4");
    params.put(Constants.GITLAB_API_URL, getServerUrl() + "/subdir/api/v4/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
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

  public void test_testConnection_transitive_rights_empty() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", getServerUrl()  + "/" + OWNER + "/" + TRANSITIVE_REPO_EMPTY));
    try {
      myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getCause().getMessage()).matches("GitLab does not grant enough permissions to publish a commit status");
    }
  }

  public void test_testConnection_transitive_rights_correct() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", getServerUrl()  + "/" + OWNER + "/" + TRANSITIVE_REPO_CORRECT));
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
    then(getRequestAsString()).isNotNull()
                              .doesNotMatch(".*error.*")
                              .matches(".*/projects\\?min_access_level=30&search=transitive_repo_correct .*");
  }

  public void test_testConnection_transitive_rights_duplicate() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", getServerUrl()  + "/" + OWNER + "/" + TRANSITIVE_REPO_DUPLICATE));
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
    then(getRequestAsString()).isNotNull()
                              .doesNotMatch(".*error.*")
                              .matches(".*/projects\\?min_access_level=30&search=transitive_repo_duplicate .*");
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
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.CANCELED.getName(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), "", null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, null, null)).getTriggeredEvent());
  }

  public void should_allow_queued_depending_on_build_type() {
    Mock buildPromotionMock = new Mock(BuildPromotionEx.class);
    Mock buildTypeMock = new Mock(BuildTypeEx.class);
    buildTypeMock.stubs().method("getFullName").withNoArguments().will(returnValue("typeFullName"));
    buildTypeMock.stubs().method("getProject").withNoArguments().will(returnValue(myBuildType.getProject()));
    buildPromotionMock.stubs().method("getBuildType").withNoArguments().will(returnValue(buildTypeMock.proxy()));
    buildPromotionMock.stubs().method("getAttribute").withAnyArguments().will(returnValue(null));
    PipelineInfo pipelineInfo = new PipelineInfo((BuildPromotionEx)buildPromotionMock.proxy());
    buildPromotionMock.stubs().method("getPipelineInfo").withNoArguments().will(returnValue(pipelineInfo));
    BuildPromotion removedBuild = (BuildPromotion)buildPromotionMock.proxy();

    GitlabPublisher publisher = (GitlabPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatus(removedBuild, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, "typeFullName", "http://localhost:8111/viewQueued.html?itemId=123")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
    assertFalse(publisher.getRevisionStatus(removedBuild, new GitLabReceiveCommitStatus(null, GitlabBuildStatus.PENDING.getName(), DefaultStatusMessages.BUILD_QUEUED, "anotherTypeFullName", "http://localhost:8111/viewQueued.html?itemId=321")).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, Long.MAX_VALUE));
  }

  public void buildFinishedSuccessfully_on_merge_result_ref() throws Exception {
    setInternalProperty("teamcity.internal." + Constants.GITLAB_FEATURE_TOGGLE_MERGE_RESULTS, true);
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
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    myFixture.addModification(modification().in(vcsRootInstance).version(mergeResultRevision).parentVersions("100000", REVISION));

    test_buildFinished_Successfully();
  }

  public void url_guessing_test() {
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/owner/project"));
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    assertEquals("https://url.com/api/v4", myPublisherSettings.guessApiURL(myVcsRoot.getProperty("url")));
  }

  public void url_guessing__port_test() {
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com:1234/owner/project"));
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    assertEquals("https://url.com:1234/api/v4", myPublisherSettings.guessApiURL(myVcsRoot.getProperty("url")));
  }

  public void url_guessing_test_http() {
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", "http://url.com/owner/project"));
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    assertEquals("http://url.com/api/v4", myPublisherSettings.guessApiURL(myVcsRoot.getProperty("url")));
  }

  public void url_guessing_test_git() {
    Map<String, String> params = getPublisherParams();
    myVcsRoot.setProperties(Collections.singletonMap("url", "git@url.com:owner/project.git"));
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    assertEquals("https://url.com/api/v4", myPublisherSettings.guessApiURL(myVcsRoot.getProperty("url")));
  }

  public void url_getting_VCS_URL_test() throws PublisherException {
    Map<String, String> params = getPublisherParams();
    params.remove(Constants.GITLAB_API_URL);
    myVcsRoot.setProperties(Collections.singletonMap("url", "git@url.com:owner/some_/path/project.git"));
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
    assertEquals("https://url.com/api/v4", ((GitlabPublisher)myPublisher).getApiUrl(myVcsRoot.getProperty("url")));
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
    myVcsModificationHistory = myFixture.getVcsHistory();
    myPublisherSettings = new GitlabSettings(new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider, myVcsModificationHistory, myOAuthConnectionsManager, myOAuthTokenStorage, getUserModelEx(),
                                             myFixture.getSecurityContext(), myFixture, myBuildNameProvider);
    Map<String, String> params = getPublisherParams();
    myPublisher = new GitlabPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>(), myVcsModificationHistory, null, myBuildNameProvider);
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
      if (url.endsWith("/refs?type=branch")) {               // /api/v4/projects/test%2Fspring-template/repository/commits/4071e1f3606ea74964146499a165b163b5f11821/refs
        final String[] tokens = url.split("/");
        respondWithCommitRefs(httpResponse, tokens[tokens.length - 2]);
      } else {
        String revision = getRevision(url, "/api/v4/projects/owner%2Fproject/repository/commits/");
        respondWithCommits(httpResponse, revision);
      }
    } else if (url.contains("/projects/" + OWNER + "%2F" + CORRECT_REPO + "/merge_requests")) {
      respondWithMergeRequest(httpResponse);
    } else if (url.contains("/projects" +  "/" + OWNER + "%2F" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, false, true, false);
    } else if (url.contains("/projects"  + "/" + OWNER + "%2F" +  GROUP_REPO)) {
      respondWithRepoInfo(httpResponse, GROUP_REPO, true, true, false);
    } else if (url.contains("/projects"  + "/" + OWNER + "%2F" +  READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false, false, false);
    } else if (url.contains("/projects"  + "/" + OWNER + "%2F" +  TRANSITIVE_REPO)) {
      respondWithRepoInfo(httpResponse, TRANSITIVE_REPO, false, false, true);
    } else if (url.contains("/projects"  + "?min_access_level=30&search=" + TRANSITIVE_REPO_CORRECT)) {
      respondWithMultipleReposInfo(httpResponse, false);
    } else if (url.contains("/projects"  + "?min_access_level=30&search=" + TRANSITIVE_REPO_DUPLICATE)) {
      respondWithMultipleReposInfo(httpResponse, true);
    } else if (url.contains("/projects"  + "?min_access_level=30&search=" + TRANSITIVE_REPO_EMPTY)) {
      respondWithEmptyReposInfo(httpResponse);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  private void respondWithCommits(HttpResponse httpResponse, String revision) {
    List<GitLabPublishCommitStatus> statuses = myRevisionToStatuses.getOrDefault(revision, new ArrayList<>());
    String json = gson.toJson(statuses.stream().map(s -> new GitLabReceiveCommitStatus(0L, s.state, s.description, s.name, s.target_url)).collect(Collectors.toList()));
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  private void respondWithMergeRequest(HttpResponse httpResponse) {
    respondFromResource(httpResponse, "/gitlab/mergeRequest.json");
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
    String revision = getRevision(url, "/api/v4/projects/owner%2Fproject/statuses/");
    if (revision != null) {
      GitLabPublishCommitStatus status = gson.fromJson(requestData, GitLabPublishCommitStatus.class);
      myRevisionToStatuses.computeIfAbsent(revision, k -> new ArrayList<>()).add(status);
    }
    return isUrlExpected(url, httpResponse);
  }


  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isGroupRepo, boolean isPushPermitted, boolean hasTransitiveRights) {
    GitLabRepoInfo repoInfo = new GitLabRepoInfo("111", new GitLabPermissions());
    GitLabAccessLevel accessLevel = new GitLabAccessLevel();
    accessLevel.access_level = isPushPermitted ? 30 : 20;
    if (isGroupRepo) {
      repoInfo.permissions.group_access = accessLevel;
    } else if (!hasTransitiveRights) {
      repoInfo.permissions.project_access = accessLevel;
    }
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  private void respondWithMultipleReposInfo(HttpResponse httpResponse, boolean twoRepos) {
    GitLabRepoInfo repoInfo = new GitLabRepoInfo("111", null);
    String jsonResponse = gson.toJson(Arrays.asList((repoInfo)));
    if (twoRepos) {
      GitLabRepoInfo repoInfoWrongId = new GitLabRepoInfo("222", null);
      jsonResponse = gson.toJson(Arrays.asList(repoInfo, repoInfoWrongId));
    }
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  private void respondWithEmptyReposInfo(HttpResponse httpResponse) {
    httpResponse.setEntity(new StringEntity("[]", "UTF-8"));
  }

  @Override
  protected boolean checkEventFinished(@NotNull String requestString, boolean isSuccessful) {
    return requestString.contains(isSuccessful ? "success" : "failure");
  }
}