package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 22/12/16.
 */

@Test
public abstract class AsyncPublisherTest extends CommitStatusPublisherTest {

  protected static final int TIMEOUT = 2000;
  protected static final int SHORT_TIMEOUT_TO_FAIL = 100;

  protected Semaphore
    myServerMutex, // released if the test wants the server to finish processing a request
    myProcessingFinished, // released by the server to indicate to the test client that it can check the request data
    myProcessingStarted; // released by the server when it has started processing a request

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myServerMutex = null;
    myProcessingFinished = new Semaphore(0);
    myProcessingStarted = new Semaphore(0);
  }

  @Override
  protected String waitForRequest() throws InterruptedException {
    return waitForRequest(TIMEOUT);
  }

  protected String waitForRequest(long timeout) throws InterruptedException {
    if (!myProcessingFinished.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
      return null;
    }
    return super.waitForRequest();
  }

  public void test_publishing_is_async() throws Exception {
    myServerMutex = new Semaphore(1);
    myServerMutex.acquire();
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    myServerMutex.release();
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }


  public void should_report_publishing_failure() throws Exception {
    setPublisherTimeout(SHORT_TIMEOUT_TO_FAIL);
    myServerMutex = new Semaphore(1);
    myServerMutex.acquire();
    // The HTTP client is supposed to wait for server for twice as less as we are waiting for its results
    // and the test HTTP server is supposed to wait for twice as much
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(myProcessingStarted.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue(); // At least one request must arrive
    // The server mutex is never released, so the server does not respond until the connection times out
    then(waitForRequest(SHORT_TIMEOUT_TO_FAIL * 2)).isNull();
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    then(problems.iterator().next().getProblem().getDescription()).matches(String.format("Commit Status Publisher.*%s.*timed out.*", myPublisher.getId()));
    myServerMutex.release();
  }

  protected void setPublisherTimeout(int timeout) {
    myPublisher.setConnectionTimeout(timeout);
  }

  public void should_publish_in_sequence() throws Exception {
    myServerMutex = new Semaphore(1);
    myServerMutex.acquire();
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    setPublisherTimeout(TIMEOUT);
    myPublisher.buildFinished(build, myRevision);
    myPublisher.buildFinished(build, myRevision);
    then(myProcessingStarted.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue(); // At least one request must arrive
    then(myServerMutex.tryAcquire(SHORT_TIMEOUT_TO_FAIL, TimeUnit.MILLISECONDS)).isFalse(); // just wait till it all fails
    then(getNumberOfCurrentRequests()).as("the second request should not be sent until the first one is processed").isEqualTo(1);
    myServerMutex.release();
  }
}
