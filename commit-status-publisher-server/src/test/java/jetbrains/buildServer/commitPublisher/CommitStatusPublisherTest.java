

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

package jetbrains.buildServer.commitPublisher;

import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import jetbrains.buildServer.BuildAgent;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.processor.FavoriteBuildProcessor;
import jetbrains.buildServer.commitPublisher.processor.predicate.BuildPredicate;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerStrategy;
import jetbrains.buildServer.messages.ErrorData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.buildDistribution.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.build.BuildSettingsOptionsImpl;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.BuildProblemsTicketManager;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class CommitStatusPublisherTest extends BaseServerTestCase {
  protected static final String REVISION = "314159";
  protected static final Long PROMOTION_ID = 1234L;
  protected static final String USER = "MyUser";
  protected static final String COMMENT = "MyComment";
  protected static final String PROBLEM_DESCR = "Problem description";
  protected static final String FEATURE_ID = "MY_FEATURE_ID";
  protected static final String BT_NAME_2BE_ESCAPED = "Name with \\ and \"";
  protected static final String BT_NAME_ESCAPED_REGEXP = BT_NAME_2BE_ESCAPED.replace("\\", "\\\\\\\\").replace("\"", "\\\\\\\"");
  private static final BuildProblemData INTERNAL_ERROR = BuildProblemData.createBuildProblem("identity", ErrorData.PREPARATION_FAILURE_TYPE, "description");
  private final AtomicBoolean myCanStartBuilds = new AtomicBoolean(true);

  protected CommitStatusPublisher myPublisher;
  protected CommitStatusPublisherSettings myPublisherSettings;
  protected CommitStatusPublisherProblems myProblems;
  protected Map<EventToTest, String> myExpectedRegExps = new HashMap<EventToTest, String>();
  protected String myVcsURL = "http://localhost/defaultvcs";
  protected String myReadOnlyVcsURL = "http://localhost/owner/readonly";
  protected SVcsRootImpl myVcsRoot;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;
  protected String myBranch;
  protected BuildRevision myRevision;
  protected SUser myUser;
  protected OAuthConnectionsManager myOAuthConnectionsManager;
  protected OAuthTokensStorage myOAuthTokenStorage;
  protected SSLTrustStoreProvider myTrustStoreProvider;

  protected enum EventToTest {
    QUEUED(Event.QUEUED), REMOVED(Event.REMOVED_FROM_QUEUE),
    STARTED(Event.STARTED), FINISHED(Event.FINISHED), FAILED(Event.FINISHED),
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
    myProblems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));
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
    setInternalProperty(BuildSettingsOptionsImpl.FREEZE_CURRENT_SETTINGS, true);
    StartBuildPrecondition startBuildPrecondition = new StartBuildPrecondition() {

      @Nullable
      @Override
      public WaitReason canStart(@NotNull QueuedBuildInfo queuedBuild,
                                 @NotNull Map<QueuedBuildInfo, BuildAgent> canBeStarted,
                                 @NotNull BuildDistributorInput buildDistributorInput,
                                 boolean emulationMode) {
        return myCanStartBuilds.get() ? null : new SimpleWaitReason("Build start is blocked");
      }
    };
    myServer.registerExtension(StartBuildPrecondition.class, "commitStatusPublisherTest", startBuildPrecondition);
    myCanStartBuilds.set(true);
  }

  public void test_testConnection() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, getPublisherParams());
    checkRequestMatch(".*error.*", EventToTest.TEST_CONNECTION);
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
    SQueuedBuild build = addBuildToQueue();
    myPublisher.buildQueued(build.getBuildPromotion(), myRevision, new AdditionalTaskInfo(build.getBuildPromotion(), DefaultStatusMessages.BUILD_QUEUED, null));
    checkRequestMatch(".*removed.*", EventToTest.QUEUED);
  }

  public void test_buildRemovedFromQueue()  throws Exception {
    if (isSkipEvent(EventToTest.REMOVED)) return;
    SQueuedBuild build = addBuildToQueue();
    build.removeFromQueue(myUser, null);
    myPublisher.buildRemovedFromQueue(build.getBuildPromotion(), myRevision, new AdditionalTaskInfo(build.getBuildPromotion(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, myUser, null));
    then(getRequestAsString()).isNotNull().matches(myExpectedRegExps.get(EventToTest.REMOVED));
  }

  public void should_publish_failure_on_failed_to_start_build() throws PublisherException {
    if (isSkipEvent(EventToTest.REMOVED)) return;

    SQueuedBuild build = addToQueue(myBuildType);
    myFixture.getBuildQueue().terminateQueuedBuild(build, null, false, null, INTERNAL_ERROR);

    myPublisher.buildRemovedFromQueue(build.getBuildPromotion(), myRevision, new AdditionalTaskInfo(build.getBuildPromotion(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null));
    checkRequestMatch(".*error.*", EventToTest.REMOVED);
  }

  public void should_publish_failure_on_canceled_build() throws PublisherException {
    if (isSkipEvent(EventToTest.REMOVED)) return;

    SQueuedBuild build = addToQueue(myBuildType);
    myFixture.getBuildQueue().terminateQueuedBuild(build, null, true, null, INTERNAL_ERROR);

    myPublisher.buildRemovedFromQueue(build.getBuildPromotion(), myRevision, new AdditionalTaskInfo(build.getBuildPromotion(), DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null));
    checkRequestMatch(".*error.*", EventToTest.REMOVED);
  }

  public void test_buildStarted() throws Exception {
    if (isSkipEvent(EventToTest.STARTED)) return;
    myPublisher.buildStarted(startBuildInCurrentBranch(myBuildType), myRevision);
    checkRequestMatch(".*error.*", EventToTest.STARTED);
  }

  public void test_buildFinished_Successfully() throws Exception {
    if (isSkipEvent(EventToTest.FINISHED)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    checkRequestMatch(".*error.*", EventToTest.FINISHED);
  }

  public void test_buildFinished_Failed() throws Exception {
    if (isSkipEvent(EventToTest.FAILED)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    checkRequestMatch(".*error.*", EventToTest.FAILED);
  }

  public void test_buildCommented_Success() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_SUCCESS)) return;
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, myUser, COMMENT, false);
    checkRequestMatch(".*error.*", EventToTest.COMMENTED_SUCCESS);
  }

  public void test_buildCommented_Failed() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_FAILED)) return;
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision, myUser, COMMENT, false);
    checkRequestMatch(".*error.*", EventToTest.COMMENTED_FAILED);
  }

  public void test_buildCommented_InProgress() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_INPROGRESS)) return;
    myPublisher.buildCommented(startBuildInCurrentBranch(myBuildType), myRevision, myUser, COMMENT, true);
    checkRequestMatch(".*error.*", EventToTest.COMMENTED_INPROGRESS);
  }

  public void test_buildCommented_InProgress_Failed() throws Exception {
    if (isSkipEvent(EventToTest.COMMENTED_INPROGRESS_FAILED)) return;
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildCommented(runningBuild, myRevision, myUser, COMMENT, true);
    checkRequestMatch(".*error.*", EventToTest.COMMENTED_INPROGRESS_FAILED);
  }


  public void test_buildInterrupted() throws Exception {
    if (isSkipEvent(EventToTest.INTERRUPTED)) return;
    SFinishedBuild finishedBuild = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    finishedBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildInterrupted(finishedBuild, myRevision);
    checkRequestMatch(".*error.*", EventToTest.INTERRUPTED);
  }

  public void test_buildFailureDetected() throws Exception {
    if (isSkipEvent(EventToTest.FAILURE_DETECTED)) return;
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildFailureDetected(runningBuild, myRevision);
    checkRequestMatch(".*error.*", EventToTest.FAILURE_DETECTED);
  }

  public void test_buildMarkedAsSuccessful() throws Exception {
    if (isSkipEvent(EventToTest.MARKED_SUCCESSFUL)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    getRequestAsString();
    myPublisher.buildMarkedAsSuccessful(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, false);
    checkRequestMatch(".*error.*", EventToTest.MARKED_SUCCESSFUL);
  }

  public void test_buildMarkedAsSuccessful_WhileRunning() throws Exception {
    if (isSkipEvent(EventToTest.MARKED_RUNNING_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(startBuildInCurrentBranch(myBuildType), myRevision, true);
    checkRequestMatch(".*error.*", EventToTest.MARKED_RUNNING_SUCCESSFUL);
  }

  public void ensure_payload_escaped() throws Exception {
    if (isSkipEvent(EventToTest.PAYLOAD_ESCAPED)) return;
    myBuildType.setName(BT_NAME_2BE_ESCAPED);
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    checkRequestMatch(".*error.*", EventToTest.PAYLOAD_ESCAPED);
  }

  public void test_should_not_publish_queued_over_final_status() throws Exception {
    if (isSkipEvent(EventToTest.QUEUED)) return;

    setInternalProperty("teamcity.commitStatusPublisher.statusCache.wildcardTtl", 0); // disable status caching when there were no statuses returned

    new CommitStatusPublisherListener(myFixture.getEventDispatcher(), new PublisherManager(myServer), myFixture.getHistory(), myFixture.getBuildsManager(), myFixture.getBuildPromotionManager(), myProblems,
                                                                               myFixture.getServerResponsibility(), myFixture.getSingletonService(ExecutorServices.class),
                                                                               myFixture.getSingletonService(ProjectManager.class), myFixture.getSingletonService(TeamCityNodes.class),
                                                                               myFixture.getSingletonService(UserModel.class), myFixture.getMultiNodeTasks(),
                                                                               Mockito.mock(FavoriteBuildProcessor.class), Mockito.mock(BuildOwnerStrategy.class), Mockito.mock(BuildPredicate.class));

    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().stream().filter(root -> root.getParent().getId() == myVcsRoot.getId()).findFirst().get();
    setUpFeature();

    addToQueueWithRevision(vcsRootInstance, "2");
    waitFor(() -> checkLastEventQueued());

    SRunningBuild build = myFixture.flushQueueAndWait();
    waitFor(() -> checkLastEventStarted());

    myFixture.finishBuild(build, false);
    waitFor(() -> checkLastEventFinished(true));

    addToQueueWithRevision(vcsRootInstance, "2");
    checkLastEventFinished(true);

    SRunningBuild build2 = myFixture.flushQueueAndWait();
    waitFor(() -> checkLastEventStarted());

    myFixture.finishBuild(build2, false);
    waitFor(() -> checkLastEventFinished(true));

    then(getAllRequestsAsString().stream().filter(request -> checkEventQueued(request)).count()).isEqualTo(1);
  }

  protected QueuedBuildEx addToQueueWithRevision(@NotNull VcsRootInstance rootInstance, @NotNull String revision) {
    QueuedBuildEx build = (QueuedBuildEx)addBuildToQueue();
    build.getBuildPromotion().setBuildRevisions(Collections.singletonList(new BuildRevisionEx(rootInstance, revision, "", revision)), 1, 1);
    myServer.getMulticaster().changesLoaded(build.getBuildPromotion());
    myCanStartBuilds.set(false);
    // flush queue to send buildPromotionSettingsFinalized event
    myFixture.flushQueue();
    myCanStartBuilds.set(true);
    return build;
  }

  protected void setUpFeature() {

    Map<String, String> featureParams = getPublisherParams();
    featureParams.put(Constants.PUBLISHER_ID_PARAM, myPublisher.getId());
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, featureParams);
    myServer.registerExtension(CommitStatusPublisherSettings.class, "test", myPublisherSettings);

    PublisherManager publisherManager = new PublisherManager(myServer);
    WebControllerManager wcm = new CommitStatusPublisherTestBase.MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    PublisherSettingsController settingsController = new PublisherSettingsController(wcm, pluginDescr, publisherManager, myProjectManager, myFixture.getSingletonService(SecurityContext.class));
    CommitStatusPublisherFeatureController featureController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, publisherManager, settingsController, myFixture.getSecurityContext());
    CommitStatusPublisherFeature feature = new CommitStatusPublisherFeature(featureController, publisherManager);
    myServer.registerExtension(BuildFeature.class, CommitStatusPublisherFeature.TYPE, feature);
  }

  protected void checkRequestMatch(String errRegex, EventToTest eventToTest) {
    then(getRequestAsString()).isNotNull().matches(myExpectedRegExps.get(eventToTest)).doesNotMatch(errRegex);
  }

  private boolean checkLastEventQueued() {
    String request = getRequestAsString();
    if (request == null) {
      return false;
    }
    return checkEventQueued(request);
  }

  protected boolean checkEventQueued(@NotNull String requestString) {
    return requestString.contains(DefaultStatusMessages.BUILD_QUEUED);
  }

  private boolean checkLastEventStarted() {
    String request = getRequestAsString();
    if (request == null) {
      return false;
    }
    return checkEventStarted(request);
  }

  protected boolean checkEventStarted(@NotNull String requestString) {
    return requestString.contains(DefaultStatusMessages.BUILD_STARTED);
  }

  private boolean checkLastEventFinished(boolean isSuccessful) {
    String request = getRequestAsString();
    if (request == null) {
      return false;
    }
    return checkEventFinished(request, isSuccessful);
  }

  protected boolean checkEventFinished(@NotNull String requestString, boolean isSuccessful) {
    return requestString.contains(isSuccessful ? "SUCCESSFUL" : "FAILED");
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
      .as(String.format("Event '%s' is supported by the publisher, but not tested", eventType))
      .isTrue();
    return !toBeTested;
  }

  protected abstract String getRequestAsString();

  protected abstract List<String> getAllRequestsAsString();

  @Nullable
  protected String getRevision(String url, String prefix) {
    if (!url.startsWith(prefix)) {
      return null;
    }
    int i = prefix.length();
    String res = "";
    while (i < url.length() && Character.isDigit(url.charAt(i))) {
      res += url.charAt(i++);
    }
    return res;
  }

  protected Set<Integer> getMatchingRequestsOrderNumbers(Pattern pattern) {
    return Collections.emptySet();
  }

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