package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.RunningBuildState;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private final long TASK_COMPLETION_TIMEOUT_MS = 3000;

  private CommitStatusPublisherListener myListener;
  private MockPublisher myPublisher;
  private PublisherLogger myLogger;
  private SUser myUser;


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new PublisherLogger();
    final PublisherManager myPublisherManager = new PublisherManager(myServer);
    final BuildHistory history = myFixture.getHistory();
    myListener = new CommitStatusPublisherListener(myFixture.getEventDispatcher(), myPublisherManager, history, myBuildsManager, myFixture.getBuildPromotionManager(), myProblems,
                                                   myFixture.getServerResponsibility(), myFixture.getSingletonService(ExecutorServices.class), myMultiNodeTasks);
    myPublisher = new MockPublisher(myPublisherSettings, MockPublisherSettings.PUBLISHER_ID, myBuildType, myFeatureDescriptor.getId(),
                                    Collections.emptyMap(), myProblems, myLogger);
    myUser = myFixture.createUserAccount("newuser");
    myPublisherSettings.setPublisher(myPublisher);
  }

  public void should_publish_started() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myListener.changesLoaded(runningBuild);
    waitForTasksToFinish(Event.STARTED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED));
  }


  public void should_publish_statuses_in_order() throws InterruptedException {
    prepareVcs();
    myPublisher.setEventToWait(Event.STARTED);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myListener.changesLoaded(runningBuild);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    myPublisher.notifyWaitingEvent(Event.STARTED, 1000);
    waitForTasksToFinish(Event.STARTED, TASK_COMPLETION_TIMEOUT_MS);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
  }

  public void should_not_accept_pending_after_finished() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.isFinishedReceived()).isTrue();
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
    myListener.changesLoaded(runningBuild);
    waitForTasksToFinish(Event.STARTED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
  }

  public void should_publish_commented() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    runningBuild.setBuildComment(myUser , "My test comment");
    waitForTasksToFinish(Event.COMMENTED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.COMMENTED));
    then(myPublisher.getLastComment()).isEqualTo("My test comment");
  }

  public void should_publish_failure() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(runningBuild, Status.NORMAL, Status.FAILURE);
    waitForTasksToFinish(Event.FAILURE_DETECTED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FAILURE_DETECTED));
    then(myPublisher.isFailureReceived()).isTrue();
  }

  public void should_publish_queued() {
    prepareVcs();
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    myListener.buildTypeAddedToQueue(queuedBuild);
    waitForTasksToFinish(Event.QUEUED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED));
  }

  public void should_publish_interrupted() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    runningBuild.setInterrupted(RunningBuildState.INTERRUPTED_BY_USER, myUser, "My reason");
    finishBuild(false);
    waitForTasksToFinish(Event.INTERRUPTED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.INTERRUPTED));
    then(myPublisher.isInterruptedReceieved()).isTrue();
  }

  // this test fails after being rewritten to use the real event due to promotion manager
  // not being able to find promotion by id, so REMOVED_FROeM_QUEUE event did never work properly
  @Test(enabled = false)
  public void should_publish_removed_from_queue() {
    prepareVcs();
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    myServer.getQueue().removeAllFromQueue();
    waitForTasksToFinish(Event.REMOVED_FROM_QUEUE, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.REMOVED_FROM_QUEUE));
    then(myPublisher.isRemovedFromQueueReceived()).isTrue();
  }

  public void should_publish_finished_success() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
    then(myPublisher.isSuccessReceived()).isTrue();
  }

  public void should_obey_publishing_disabled_property() {
    prepareVcs();
    setInternalProperty("teamcity.commitStatusPublisher.enabled", "false");
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_obey_publishing_disabled_parameter() {
    prepareVcs();
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.enabled", "false"));
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_give_a_priority_to_publishing_enabled_parameter() {
    prepareVcs();
    setInternalProperty("teamcity.commitStatusPublisher.enabled", "false");
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.enabled", "true"));
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
  }

  public void should_give_a_priority_to_publishing_disabled_parameter() {
    prepareVcs();
    setInternalProperty("teamcity.commitStatusPublisher.enabled", "true");
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.enabled", "false"));
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_publish_for_multiple_roots() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    // This documents the current behaviour, i.e. only one QUEUED event received for some reason
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED,
                                                                  Event.STARTED, Event.STARTED, Event.STARTED,
                                                                  Event.FINISHED, Event.FINISHED, Event.FINISHED));
    then(myPublisher.successReceived()).isEqualTo(3);
  }

  public void should_publish_to_specified_root_with_multiple_roots_attached() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.EXT_ID);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    // This documents the current behaviour, i.e. no QUEUED event received
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.STARTED, Event.FINISHED));
    then(myPublisher.successReceived()).isEqualTo(1);
  }

  public void should_handle_publisher_exception() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myPublisher.shouldThrowException();
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    SystemProblem problem = problems.iterator().next().getProblem();
    then(problem.getDescription())
      .contains("Commit Status Publisher")
      .contains("buildFinished")
      .contains(MockPublisher.PUBLISHER_ERROR)
      .contains(myPublisher.getId());
  }

  public void should_handle_async_errors() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
  }

  public void should_retain_all_errors_for_multiple_roots() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger); // This problem should be cleaned during buildFinished(...)
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myPublisher.shouldReportError();
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger); // and one more - later
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(4); // Must be 4 in total, neither 1 nor 5
  }


  public void should_not_publish_additional_status_if_marked_successful() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(runningBuild, Status.FAILURE, Status.NORMAL);
    assertIfNoTasks(Event.FAILURE_DETECTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED));
    then(myPublisher.isFailureReceived()).isFalse();
  }

  @TestFor(issues = "TW-47724")
  public void should_not_publish_status_for_personal_builds() throws IOException {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startPersonalBuild(myUser, myBuildType);
    myFixture.finishBuild(runningBuild, false);
    assertIfNoTasks(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
    then(myPublisher.isSuccessReceived()).isFalse();
  }

  private void prepareVcs() {
   prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.EXT_ID);
  }

  private void prepareVcs(String vcsRootName, String currentVersion, String revNo, SetVcsRootIdMode setVcsRootIdMode) {
    final SVcsRoot vcsRoot = myFixture.addVcsRoot("jetbrains.git", vcsRootName);
    switch (setVcsRootIdMode) {
      case EXT_ID:
        myPublisher.setVcsRootId(vcsRoot.getExternalId());
        break;
      case INT_ID:
        myPublisher.setVcsRootId(String.valueOf(vcsRoot.getId()));
    }
    myBuildType.addVcsRoot(vcsRoot);
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    myCurrentVersions.put(vcsRoot.getName(), currentVersion);
    myFixture.addModification(new ModificationData(new Date(),
            Collections.singletonList(new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file","1", "2")),
            "descr2", "user", vcsRootInstance, revNo, revNo));
  }

  private void waitForTasksToFinish(Event eventType, long timeout) {
    waitFor(() -> myMultiNodeTasks.findFinishedTasks(Collections.singleton(eventType.getName()), Dates.ONE_MINUTE).stream().findAny().isPresent(), timeout);
  }

  private void assertIfNoTasks(Event eventType) {
    then(myMultiNodeTasks.findTasks(Collections.singleton(eventType.getName())).isEmpty()).isTrue();
  }

  private enum SetVcsRootIdMode { DONT, EXT_ID, INT_ID }
}
