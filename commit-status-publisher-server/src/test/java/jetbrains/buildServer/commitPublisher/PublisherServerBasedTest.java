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
  protected static final int TIMEOUT = 3000;
  protected Semaphore myServerMutex, myClientMutex;

  protected CommitStatusPublisher myPublisher;
  protected CommitStatusPublisherProblems myProblems;
  protected Map<Events, String> myExpectedRegExps = new HashMap<Events, String>();
  protected SimpleExecutorServices myExecServices;
  protected String myVcsURL = "http://localhost/defaultvcs";

  private BuildRevision myRevision;
  private SUser myUser;
  private SystemProblemNotificationEngine myProblemNotificationEngine;

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
    SVcsRoot vcsRoot = myFixture.addVcsRoot("jetbrains.git", "", myBuildType);
    vcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(vcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myUser = myFixture.createUserAccount(USER);
    myExecServices = myFixture.getSingletonService(SimpleExecutorServices.class);
    myExecServices.start();
    myServerMutex = new Semaphore(1);
    myClientMutex = new Semaphore(0);
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myProblemNotificationEngine);
  }


  public void test_buildQueued() throws InterruptedException {
    if (!isToBeTested(Events.QUEUED)) return;
    myPublisher.buildQueued(myBuildType.addToQueue(""), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.QUEUED));
  }

  public void test_buildRemovedFromQueue()  throws InterruptedException {
    if (!isToBeTested(Events.REMOVED)) return;
    myPublisher.buildRemovedFromQueue(myBuildType.addToQueue(""), myRevision, myUser, COMMENT);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.REMOVED));
  }

  public void test_buildStarted() throws InterruptedException {
    if (!isToBeTested(Events.STARTED)) return;
    myPublisher.buildStarted(myFixture.startBuild(myBuildType), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.STARTED));
  }

  public void test_buildFinished_Successfully() throws InterruptedException {
    if (!isToBeTested(Events.FINISHED)) return;
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FINISHED));
  }

  public void test_publishing_is_async() throws InterruptedException {
    myServerMutex.acquire();
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision);
    myServerMutex.release();
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FINISHED));
  }


  public void test_publishing_failure_reported() throws InterruptedException {
    myServerMutex.acquire();
    // The HTTP client is supposed to wait for server for twice as less as we are waiting for its results
    // and the test HTTP server is supposed to wait for twice as much
    myPublisher.setConnectionTimeout(TIMEOUT / 2);
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision);
    // The server mutex is never released, so the server does not respond until it times out
    then(waitForRequest()).isNull();
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    then(problems.iterator().next().getProblem().getDescription()).matches(String.format("Publish status error in build.*%s.*timed out.*", myBuildType.getExternalId()));
    myServerMutex.release();
  }

  public void test_buildFinished_Failed() throws InterruptedException {
    if (!isToBeTested(Events.FAILED)) return;
    myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.FAILURE), myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FAILED));
  }

  public void test_buildCommented_Success() throws InterruptedException {
    if (!isToBeTested(Events.COMMENTED_SUCCESS)) return;
    myPublisher.buildCommented(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_SUCCESS));
  }

  public void test_buildCommented_Failed() throws InterruptedException {
    if (!isToBeTested(Events.COMMENTED_FAILED)) return;
    myPublisher.buildCommented(myFixture.createBuild(myBuildType, Status.FAILURE), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_FAILED));
  }

  public void test_buildCommented_InProgress() throws InterruptedException {
    if (!isToBeTested(Events.COMMENTED_INPROGRESS)) return;
    myPublisher.buildCommented(myFixture.startBuild(myBuildType), myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_INPROGRESS));
  }

  public void test_buildCommented_InProgress_Failed() throws InterruptedException {
    if (!isToBeTested(Events.COMMENTED_INPROGRESS_FAILED)) return;
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildCommented(runningBuild, myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.COMMENTED_INPROGRESS_FAILED));
  }


  public void test_buildInterrupted() throws InterruptedException {
    if (!isToBeTested(Events.INTERRUPTED)) return;
    SFinishedBuild finishedBuild = myFixture.createBuild(myBuildType, Status.NORMAL);
    finishedBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildInterrupted(finishedBuild, myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.INTERRUPTED));
  }

  public void test_buildFailureDetected() throws InterruptedException {
    if (!isToBeTested(Events.FAILURE_DETECTED)) return;
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildFailureDetected(runningBuild, myRevision);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.FAILURE_DETECTED));
  }

  public void test_buildMarkedAsSuccessful() throws InterruptedException {
    if (!isToBeTested(Events.MARKED_SUCCESSFUL)) return;
    myPublisher.buildMarkedAsSuccessful(myFixture.createBuild(myBuildType, Status.NORMAL), myRevision, false);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(Events.MARKED_SUCCESSFUL));
  }

  public void test_buildMarkedAsSuccessful_WhileRunning() throws InterruptedException {
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
    myClientMutex.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS);
    return getRequestAsString();
  }

  protected abstract String getRequestAsString();

}
