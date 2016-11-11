package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.executors.SimpleExecutorServices;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

  protected CommitStatusPublisher myPublisher;
  protected CommitStatusPublisherSettings myPublisherSettings;
  protected CommitStatusPublisherProblems myProblems;
  protected Map<Events, String> myExpectedRegExps = new HashMap<Events, String>();
  protected SimpleExecutorServices myExecServices;
  protected String myVcsURL = "http://localhost/defaultvcs";
  protected String myReadOnlyVcsURL = "http://localhost/owner/readonly";
  protected SVcsRoot myVcsRoot;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;
  protected String myBranch;
  protected BuildRevision myRevision;
  protected SUser myUser;
  protected OAuthConnectionsManager myOAuthConnectionsManager;
  protected OAuthTokensStorage myOAuthTokenStorage;

  protected enum Events {
    QUEUED, REMOVED, STARTED,
    FINISHED, FAILED,
    COMMENTED_SUCCESS, COMMENTED_FAILED,
    COMMENTED_INPROGRESS, COMMENTED_INPROGRESS_FAILED,
    INTERRUPTED, FAILURE_DETECTED,
    MARKED_SUCCESSFUL, MARKED_RUNNING_SUCCESSFUL,
    TEST_CONNECTION
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcsRoot = myFixture.addVcsRoot("jetbrains.git", "", myBuildType);
    myVcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myUser = myFixture.createUserAccount(USER);
    myExecServices = myFixture.getSingletonService(SimpleExecutorServices.class);
    myExecServices.start();
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myProblemNotificationEngine);
    myBranch = null;
    myOAuthConnectionsManager = new OAuthConnectionsManager(myServer);
    myOAuthTokenStorage =  new OAuthTokensStorage(myFixture.getServerPaths(), myFixture.getSingletonService(ExecutorServices.class));
  }

  public void test_testConnection() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) return;
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, getPublisherParams());
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.TEST_CONNECTION));
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
    if (!isToBeTested(Events.QUEUED)) return;
    myPublisher.buildQueued(myBuildType.addToQueue(""), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.QUEUED));
  }

  public void test_buildRemovedFromQueue()  throws Exception {
    if (!isToBeTested(Events.REMOVED)) return;
    myPublisher.buildRemovedFromQueue(myBuildType.addToQueue(""), myRevision, myUser, COMMENT);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.REMOVED));
  }

  public void test_buildStarted() throws Exception {
    if (!isToBeTested(Events.STARTED)) return;
    myPublisher.buildStarted(startBuildInCurrentBranch(myBuildType), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.STARTED));
  }

  public void test_buildFinished_Successfully() throws Exception {
    if (!isToBeTested(Events.FINISHED)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FINISHED));
  }

  // the implementation must return the number of publishing requests currently being processed by the mock server
  protected abstract int getNumberOfCurrentRequests();

  public void test_buildFinished_Failed() throws Exception {
    if (!isToBeTested(Events.FAILED)) return;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FAILED));
  }

  public void test_buildCommented_Success() throws Exception {
    if (!isToBeTested(Events.COMMENTED_SUCCESS)) return;
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_SUCCESS));
  }

  public void test_buildCommented_Failed() throws Exception {
    if (!isToBeTested(Events.COMMENTED_FAILED)) return;
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_FAILED));
  }

  public void test_buildCommented_InProgress() throws Exception {
    if (!isToBeTested(Events.COMMENTED_INPROGRESS)) return;
    myPublisher.buildCommented(startBuildInCurrentBranch(myBuildType), myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_INPROGRESS));
  }

  public void test_buildCommented_InProgress_Failed() throws Exception {
    if (!isToBeTested(Events.COMMENTED_INPROGRESS_FAILED)) return;
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildCommented(runningBuild, myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_INPROGRESS_FAILED));
  }


  public void test_buildInterrupted() throws Exception {
    if (!isToBeTested(Events.INTERRUPTED)) return;
    SFinishedBuild finishedBuild = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    finishedBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildInterrupted(finishedBuild, myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.INTERRUPTED));
  }

  public void test_buildFailureDetected() throws Exception {
    if (!isToBeTested(Events.FAILURE_DETECTED)) return;
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildFailureDetected(runningBuild, myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FAILURE_DETECTED));
  }

  public void test_buildMarkedAsSuccessful() throws Exception {
    if (!isToBeTested(Events.MARKED_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.MARKED_SUCCESSFUL));
  }

  public void test_buildMarkedAsSuccessful_WhileRunning() throws Exception {
    if (!isToBeTested(Events.MARKED_RUNNING_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(startBuildInCurrentBranch(myBuildType), myRevision, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.MARKED_RUNNING_SUCCESSFUL));
  }

  private boolean isToBeTested(@NotNull Events eventType) {
    // the key must be there, to enforce explicit "not-to-be tested" declaration
    then(myExpectedRegExps.containsKey(eventType))
            .as(String.format("Event '%s' must either be tested or explicitly declared as not to be tested.", eventType.toString()))
            .isTrue();
    // if corresponding expected value is null, it is not to be tested
    return null != myExpectedRegExps.get(eventType);
  }

  protected String waitForRequest() throws InterruptedException {
    return getRequestAsString();
  }

  protected abstract String getRequestAsString();

  protected SRunningBuild startBuildInCurrentBranch(SBuildType buildType) {
    return null == myBranch ? startBuild(buildType) : startBuildInBranch(buildType, myBranch);
  }

  protected SFinishedBuild createBuildInCurrentBranch(SBuildType buildType, Status status) {
    return null == myBranch ? createBuild(buildType, status) : createBuildInBranch(buildType, myBranch, status);
  }


}
