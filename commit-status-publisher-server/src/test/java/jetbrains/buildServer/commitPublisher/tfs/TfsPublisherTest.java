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

package jetbrains.buildServer.commitPublisher.tfs;

import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.04.2017
 *         Time: 18:06
 */
@Test
public class TfsPublisherTest extends HttpPublisherTest {
  private Map<String, String> myParams = getPublisherParams();

  TfsPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*pending.*%s.*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*pending.*%s\".*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE));
    myExpectedRegExps.put(EventToTest.STARTED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*pending.*is pending.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*succeeded.*has succeeded.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*failed.*has failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*failed.*has failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*succeeded.*has succeeded.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*pending.*is pending.*", REVISION));
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*failed.*%s.*has failed.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, null); // not to be tested
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPublisherSettings = new TfsPublisherSettings(new MockPluginDescriptor(), myWebLinks, myProblems,
                                                   myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext(), myTrustStoreProvider);
    myPublisher = new TfsStatusPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, myParams, myProblems, new CommitStatusesCache<>());
    myVcsURL = getServerUrl() + "/_git/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl()  + "/_git/" + READ_ONLY_REPO;
    myVcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));
  }

  public void should_fail_with_error_on_wrong_vcs_url() {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getMessage()).matches("Invalid URL for TFS Git project.*" + myVcsRoot.getProperty("url") + ".*");
    }
  }

  public void shoudld_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    TfsStatusPublisher publisher = (TfsStatusPublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (TfsStatusPublisher.CommitStatus)null));
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Succeeded.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Failed.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Error.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus("nonsense", null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), "nonsense", null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), DefaultStatusMessages.BUILD_QUEUED, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.STARTED, publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), DefaultStatusMessages.BUILD_STARTED, null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null, null)).getTriggeredEvent());
  }

  public void should_define_correctly_if_event_allowed() {
    MockQueuedBuild removedBuild = new MockQueuedBuild();
    removedBuild.setBuildTypeId("buildType");
    removedBuild.setItemId("123");
    TfsStatusPublisher publisher = (TfsStatusPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=123", null)).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(), DefaultStatusMessages.BUILD_QUEUED, "http://localhost:8111/viewQueued.html?itemId=321", null)).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
  }

  @Override
  public void test_testConnection() throws Exception {
    try {
      super.test_testConnection();
    } catch (PublisherException e) {
      Assert.assertTrue(e.getMessage().startsWith("TFS publisher has failed to test connection to repository"));
    }
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(TfsConstants.ACCESS_TOKEN, "token");
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains(READ_ONLY_REPO)) {
      httpResponse.setStatusCode(403);
      httpResponse.setEntity(new StringEntity("{'message': 'error'}", StandardCharsets.UTF_8));
      return false;
    } else if (url.contains("statuses?api-version=2.1")) {
      respondWithStatuses(httpResponse);
    } else if (url.contains("/commits/" + REVISION)) {
      respondWithCommits(httpResponse);
    } else if (url.contains("42/iterations")) {
      respondWithSameTargetCommitsIterations(httpResponse);
    }
    return true;
  }

  private void respondWithCommits(HttpResponse httpResponse) {
    TfsStatusPublisher.Commit obj = new TfsStatusPublisher.Commit();
    obj.commitId = REVISION;
    obj.parents = ImmutableList.of("f0adfd81352412ec91254943230c13c4c95c9f9f", "7c1795ca5bb609008e443498a18a2691b2738ecf");
    TfsStatusPublisher.Author author = new TfsStatusPublisher.Author();
    author.name = "";
    obj.author = author;
    String json = gson.toJson(obj);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
    httpResponse.setStatusCode(200);
  }

  private void respondWithSameTargetCommitsIterations(HttpResponse httpResponse) {
    TfsStatusPublisher.IterationsList obj = new TfsStatusPublisher.IterationsList();
    obj.value = new ArrayList<>();
    obj.value.add(createIteration("1", "2023-01-16T17:52:44.3758989Z", "e0adfd81352412ec91254943230c13c4c95c9f9f", "7c1795ca5bb609008e443498a18a2691b2738ecf"));
    obj.value.add(createIteration("2", "2023-01-16T18:52:44.3758989Z", "f0adfd81352412ec91254943230c13c4c95c9f9f", "7c1795ca5bb609008e443498a18a2691b2738ecf"));
    obj.value.add(createIteration("3", "2023-01-16T19:52:44.3758989Z", "f0adfd81352412ec91254943230c13c4c95c9f9f", "7c1795ca5bb609008e443498a18a2691b2738ecf"));
    String json = gson.toJson(obj);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
    httpResponse.setStatusCode(200);
  }

  private TfsStatusPublisher.Iteration createIteration(String id, String createDate, String srcSha, String trgtSha) {
    TfsStatusPublisher.Iteration i = new TfsStatusPublisher.Iteration();
    i.id = id;
    i.createdDate = createDate;
    TfsStatusPublisher.IterationCommit commit = new TfsStatusPublisher.IterationCommit();
    commit.commitId = trgtSha;
    i.targetRefCommit = commit;
    commit = new TfsStatusPublisher.IterationCommit();
    commit.commitId = srcSha;
    i.sourceRefCommit = commit;
    return i;
  }

  private void respondWithStatuses(HttpResponse httpResponse) {
    TfsStatusPublisher.CommitStatuses commitStatuses = new TfsStatusPublisher.CommitStatuses();
    TfsStatusPublisher.StatusContext context = new TfsStatusPublisher.StatusContext();
    context.genre = "TeamCity";
    context.name = "MyDefaultTestBuildType";
    TfsStatusPublisher.CommitStatus status = new TfsStatusPublisher.CommitStatus(TfsStatusPublisher.StatusState.Pending.getName(),
                                                                                 DefaultStatusMessages.BUILD_QUEUED, "", context);
    commitStatuses.value = Collections.singleton(status);
    String json = gson.toJson(commitStatuses);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }

  public void should_publish_status_to_correct_commit_for_pr() throws Exception {
    TfsStatusPublisher publisher = (TfsStatusPublisher)myPublisher;
    myParams.put(TfsConstants.PUBLISH_PULL_REQUESTS, "true");
    myBranch = "refs/pull/42/merge";
    BuildPromotionEx promotion = createPromotionWithDesiredBranch(myBuildType, myBranch);
    BuildRevision revision = new BuildRevision(myRevision.getEntry(), new RepositoryVersion(REVISION, REVISION, myBranch));
    myPublisher.buildQueued(promotion, revision, new AdditionalTaskInfo(null, null));
    waitFor(() -> {
      int countRequests = countRequests();
      if (countRequests < 4) return false;
      Set<Integer> matchingRequestsOrderNumbers = getMatchingRequestsOrderNumbers(Pattern.compile("POST.+\\/pullRequests\\/42/iterations/3.+"));
      if (matchingRequestsOrderNumbers.isEmpty()) return false;
      return matchingRequestsOrderNumbers.contains((countRequests - 1) - 1);  // last but one, counting from 0
    }, 3000);
  }
}
