/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher;

import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class CommitStatusPublisherTest extends BaseServerTestCase {
  protected static final String REVISION = "314159";
  protected static final String USER = "MyUser";
  protected static final String COMMENT = "MyComment";
  protected static final String PROBLEM_DESCR = "Problem description";
  protected static final String FEATURE_ID = "MY_FEATURE_ID";
  protected static final String BT_NAME_2BE_ESCAPED = "Name with \\ and \"";
  protected static final String BT_NAME_ESCAPED_REGEXP = BT_NAME_2BE_ESCAPED.replace("\\", "\\\\\\\\").replace("\"", "\\\\\\\"");

  protected CommitStatusPublisher myPublisher;
  protected CommitStatusPublisherSettings myPublisherSettings;
  protected CommitStatusPublisherProblems myProblems;
  protected Map<EventToTest, String> myExpectedRegExps = new HashMap<EventToTest, String>();
  protected String myVcsURL = "http://localhost/defaultvcs";
  protected String myReadOnlyVcsURL = "http://localhost/owner/readonly";
  protected SVcsRoot myVcsRoot;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;
  protected String myBranch;
  protected BuildRevision myRevision;
  protected SUser myUser;
  protected OAuthConnectionsManager myOAuthConnectionsManager;
  protected OAuthTokensStorage myOAuthTokenStorage;
  protected SSLTrustStoreProvider myTrustStoreProvider;


  protected enum EventToTest {
    QUEUED(Event.QUEUED), REMOVED(Event.REMOVED_FROM_QUEUE), STARTED(Event.STARTED),
    FINISHED(Event.FINISHED), FAILED(Event.FINISHED),
    COMMENTED_SUCCESS(Event.COMMENTED), COMMENTED_FAILED(Event.COMMENTED),
    COMMENTED_INPROGRESS(Event.COMMENTED), COMMENTED_INPROGRESS_FAILED(Event.COMMENTED),
    INTERRUPTED(Event.INTERRUPTED), FAILURE_DETECTED(Event.FAILURE_DETECTED),
    MARKED_SUCCESSFUL(Event.MARKED_AS_SUCCESSFUL), MARKED_RUNNING_SUCCESSFUL(Event.MARKED_AS_SUCCESSFUL),
    TEST_CONNECTION(null), PAYLOAD_ESCAPED(Event.FINISHED);

    private final Event myEvent;

    EventToTest(Event event) {
      myEvent = event;
    }

    public Event getEvent() {
      return myEvent;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcsRoot = myFixture.addVcsRoot("jetbrains.git", "", myBuildType);
    myVcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myUser = myFixture.createUserAccount(USER);
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myProblemNotificationEngine);
    myBranch = null;
    myOAuthConnectionsManager = myFixture.getSingletonService(OAuthConnectionsManager.class);
    myOAuthTokenStorage = myFixture.getSingletonService(OAuthTokensStorage.class);
    myTrustStoreProvider = new SSLTrustStoreProvider() {
      @Nullable
      @Override
      public KeyStore getTrustStore() {
        return null;
      }
    };
  }

  public void test_testConnection() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, getPublisherParams());
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.TEST_CONNECTION));
  }

  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    test_testConnection_failure(myReadOnlyVcsURL, getPublisherParams());
  }

  public void test_testConnection_fails_on_bad_repo_url() throws InterruptedException {
    test_testConnection_failure("http://localhost/nothing", getPublisherParams());
  }

  public void test_testConnection_fails_on_missing_target() throws InterruptedException {
    test_testConnection_failure("http://localhost/nouser/norepo", getPublisherParams());
  }

  protected void test_testConnection_failure(String repoURL, Map <String, String> params) throws InterruptedException {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    myVcsRoot.setProperties(Collections.singletonMap("url", repoURL));
    try {
      myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
      fail("Connection testing failure must throw PublishError exception");
    } catch (PublisherException ex) {
      // success
    }
  }

  protected abstract Map<String, String> getPublisherParams();

  public void test_buildQueued() throws Exception {
    if (isSkipEvent(EventToTest.QUEUED)) return;
    myPublisher.buildQueued(addBuildToQueue(), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.QUEUED));
  }

  public void test_buildRemovedFromQueue()  throws Exception {
    if (isSkipEvent(EventToTest.REMOVED)) return;
    myPublisher.buildRemovedFromQueue(addBuildToQueue(), myRevision, myUser, COMMENT);
    then(getRequestAsString()).isNotNull().matches(myExpectedRegExps.get(EventToTest.REMOVED));
  }

  public void test_buildStarted() throws Exception {
    if (isSkipEvent(EventToTest.STARTED)) return;
    myPublisher.buildStarted(startBuildInCurrentBranch(myBuildType), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.STARTED));
  }

  public void test_buildFinished_Successfully() throws Exception {
    if (isSkipEvent(EventToTest.FINISHED)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  public void test_buildFinished_Failed() throws Exception {
    if (isSkipEvent(EventToTest.FAILED)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FAILED));
  }

  public void test_buildCommented_Success() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_SUCCESS)) return;
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, myUser, COMMENT, false);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.COMMENTED_SUCCESS));
  }

  public void test_buildCommented_Failed() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_FAILED)) return;
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision, myUser, COMMENT, false);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.COMMENTED_FAILED));
  }

  public void test_buildCommented_InProgress() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_INPROGRESS)) return;
    myPublisher.buildCommented(startBuildInCurrentBranch(myBuildType), myRevision, myUser, COMMENT, true);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.COMMENTED_INPROGRESS));
  }

  public void test_buildCommented_InProgress_Failed() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_INPROGRESS_FAILED)) return;
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildCommented(runningBuild, myRevision, myUser, COMMENT, true);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.COMMENTED_INPROGRESS_FAILED));
  }


  public void test_buildInterrupted() throws Exception {
    if (isSkipEvent(EventToTest.INTERRUPTED)) return;
    SFinishedBuild finishedBuild = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    finishedBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildInterrupted(finishedBuild, myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.INTERRUPTED));
  }

  public void test_buildFailureDetected() throws Exception {
    if (isSkipEvent(EventToTest.FAILURE_DETECTED)) return;
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildFailureDetected(runningBuild, myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FAILURE_DETECTED));
  }

  public void test_buildMarkedAsSuccessful() throws Exception {
    if (isSkipEvent(EventToTest.MARKED_SUCCESSFUL)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    getRequestAsString();
    myPublisher.buildMarkedAsSuccessful(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, false);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.MARKED_SUCCESSFUL));
  }

  public void test_buildMarkedAsSuccessful_WhileRunning() throws Exception {
    if (isSkipEvent(EventToTest.MARKED_RUNNING_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(startBuildInCurrentBranch(myBuildType), myRevision, true);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.MARKED_RUNNING_SUCCESSFUL));
  }

  public void ensure_payload_escaped() throws Exception {
    if (isSkipEvent(EventToTest.PAYLOAD_ESCAPED)) return;
    myBuildType.setName(BT_NAME_2BE_ESCAPED);
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.PAYLOAD_ESCAPED));
  }

  private boolean isSkipEvent(@NotNull EventToTest eventType) {
    String regExp = myExpectedRegExps.get(eventType);
    boolean toBeTested = null != regExp;

    Event event = eventType.getEvent();
    if (null != event && !myPublisher.isEventSupported(event)) {
      then(toBeTested).as("Unsupported event has been tested").isFalse();
      return true;
    }

    then(null == event || toBeTested)
      .as(String.format("Event '%s' is supported by the publisher, but not tested", eventType.toString()))
      .isTrue();
    return !toBeTested;
  }

  protected abstract String getRequestAsString();

  protected SRunningBuild startBuildInCurrentBranch(SBuildType buildType) {
    return null == myBranch ? startBuild(buildType) : startBuildInBranch(buildType, myBranch);
  }

  protected SFinishedBuild createBuildInCurrentBranch(SBuildType buildType, Status status) {
    return null == myBranch ? createBuild(buildType, status) : createBuildInBranch(buildType, myBranch, status);
  }

  @NotNull
  protected SQueuedBuild addBuildToQueue() {
    return Objects.requireNonNull(myBuildType.addToQueue(""));
  }

}
