package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.executors.SimpleExecutorServices;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class PublisherServerBasedTest extends BaseServerTestCase {
  protected static final String REVISION = "314159";
  protected static final String USER = "MyUser";
  protected static final String COMMENT = "MyComment";
  protected static final String PROBLEM_DESCR = "Problem description";
  protected static final String FEATURE_ID = "MY_FEATURE_ID";
  protected static final int TIMEOUT = 2000;
  protected Semaphore
          myServerMutex, // released if the test wants the server to finish processing a request
          myProcessingFinished, // released by the server to indicate to the test client that it can check the request data
          myProcessingStarted; // released by the server when it has started processing a request

  protected CommitStatusPublisher myPublisher;
  protected CommitStatusPublisherProblems myProblems;
  protected Map<Events, String> myExpectedRegExps = new HashMap<Events, String>();
  protected SimpleExecutorServices myExecServices;
  protected String myVcsURL = "http://localhost/defaultvcs";
  protected SVcsRoot myVcsRoot;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;

  private BuildRevision myRevision;
  private SUser myUser;

  protected enum Events {
    QUEUED, REMOVED, STARTED,
    FINISHED, FAILED,
    COMMENTED_SUCCESS, COMMENTED_FAILED,
    COMMENTED_INPROGRESS, COMMENTED_INPROGRESS_FAILED,
    INTERRUPTED, FAILURE_DETECTED,
    MARKED_SUCCESSFUL, MARKED_RUNNING_SUCCESSFUL
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
    myServerMutex = new Semaphore(1);
    myProcessingFinished = new Semaphore(0);
    myProcessingStarted = new Semaphore(0);
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myProblemNotificationEngine);
  }


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
    myPublisher.buildStarted(myFixture.startBuild(myBuildType), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.STARTED));
  }

  public void test_buildFinished_Successfully() throws Exception {
    if (!isToBeTested(Events.FINISHED)) return;
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FINISHED));
  }

  public void test_publishing_is_async() throws Exception {
    myServerMutex.acquire();
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision);
    myServerMutex.release();
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FINISHED));
  }


  public void should_report_publishing_failure() throws Exception {
    myServerMutex.acquire();
    // The HTTP client is supposed to wait for server for twice as less as we are waiting for its results
    // and the test HTTP server is supposed to wait for twice as much
    myPublisher.setConnectionTimeout(TIMEOUT / 2);
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision);
    // The server mutex is never released, so the server does not respond until it times out
    then(waitForRequest()).isNull();
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    then(problems.iterator().next().getProblem().getDescription()).matches(String.format("Commit Status Publisher.*%s.*timed out.*", myPublisher.getId()));
    myServerMutex.release();
  }

  // temporarily disabled due to flaky behaviour
  public void should_publish_in_sequence() throws Exception {
    myServerMutex.acquire();
    SFinishedBuild build = myFixture.createBuild(myBuildType, Status.NORMAL);
    myPublisher.setConnectionTimeout(TIMEOUT);
    myPublisher.buildFinished(build, myRevision);
    myPublisher.buildFinished(build, myRevision);
    then(myProcessingStarted.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue(); // At least one request must arrive
    then(myServerMutex.tryAcquire(TIMEOUT / 2, TimeUnit.MILLISECONDS)).isFalse(); // just wait till it all fails
    then(getNumberOfCurrentRequests()).as("the second request should not be sent until the first one is processed").isEqualTo(1);
    myServerMutex.release();
  }

  // the implementation must return the number of publishing requests currently being processed by the mock server
  protected abstract int getNumberOfCurrentRequests();

  public void test_buildFinished_Failed() throws Exception {
    if (!isToBeTested(Events.FAILED)) return;
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.FAILURE), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FAILED));
  }

  public void test_buildCommented_Success() throws Exception {
    if (!isToBeTested(Events.COMMENTED_SUCCESS)) return;
    myPublisher.buildCommented(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_SUCCESS));
  }

  public void test_buildCommented_Failed() throws Exception {
    if (!isToBeTested(Events.COMMENTED_FAILED)) return;
    myPublisher.buildCommented(myFixture.createBuild(myBuildType, Status.FAILURE), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_FAILED));
  }

  public void test_buildCommented_InProgress() throws Exception {
    if (!isToBeTested(Events.COMMENTED_INPROGRESS)) return;
    myPublisher.buildCommented(myFixture.startBuild(myBuildType), myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_INPROGRESS));
  }

  public void test_buildCommented_InProgress_Failed() throws Exception {
    if (!isToBeTested(Events.COMMENTED_INPROGRESS_FAILED)) return;
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildCommented(runningBuild, myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_INPROGRESS_FAILED));
  }


  public void test_buildInterrupted() throws Exception {
    if (!isToBeTested(Events.INTERRUPTED)) return;
    SFinishedBuild finishedBuild = myFixture.createBuild(myBuildType, Status.NORMAL);
    finishedBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildInterrupted(finishedBuild, myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.INTERRUPTED));
  }

  public void test_buildFailureDetected() throws Exception {
    if (!isToBeTested(Events.FAILURE_DETECTED)) return;
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildFailureDetected(runningBuild, myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FAILURE_DETECTED));
  }

  public void test_buildMarkedAsSuccessful() throws Exception {
    if (!isToBeTested(Events.MARKED_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.MARKED_SUCCESSFUL));
  }

  public void test_buildMarkedAsSuccessful_WhileRunning() throws Exception {
    if (!isToBeTested(Events.MARKED_RUNNING_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(myFixture.startBuild(myBuildType), myRevision, true);
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
    myProcessingFinished.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS);
    return getRequestAsString();
  }

  protected abstract String getRequestAsString();

}
