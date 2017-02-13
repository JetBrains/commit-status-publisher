package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BuildPromotionImpl;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static jetbrains.buildServer.serverSide.BuildAttributes.COLLECT_CHANGES_ERROR;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private static final String PUBLISHER_ERROR = "Simulated publisher exception";

  private CommitStatusPublisherListener myListener;
  private MockPublisherSettings myPublisherSettings;
  private MockPublisherRegisterFailure myPublisher;
  private PublisherLogger myLogger;


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new PublisherLogger();
    myPublisherSettings = new MockPublisherSettings(myProblems);
    final PublisherManager myPublisherManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>singletonList(myPublisherSettings));
    final BuildHistory history = myFixture.getHistory();
    myListener = new CommitStatusPublisherListener(EventDispatcher.create(BuildServerListener.class), myPublisherManager, history, myRBManager, myProblems);
    myPublisher = new MockPublisherRegisterFailure(myBuildType, myProblems);
    myPublisherSettings.setPublisher(myPublisher);
  }

  public void should_publish_failure() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(runningBuild, Status.NORMAL, Status.FAILURE);
    then(myPublisher.isFailureReceived()).isTrue();
  }

  public void should_publish_finished_success() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    then(myPublisher.isFinishedReceived()).isTrue();
    then(myPublisher.isSuccessReceived()).isTrue();
  }

  public void should_publish_for_multiple_roots() {
    prepareVcs("vcs1", "111", "rev1_2", false);
    prepareVcs("vcs2", "222", "rev2_2", false);
    prepareVcs("vcs3", "333", "rev3_2", false);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    then(myPublisher.finishedReceived()).isEqualTo(3);
    then(myPublisher.successReceived()).isEqualTo(3);
  }


  public void should_handle_publisher_exception() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myPublisher.shouldThrowException();
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    SystemProblem problem = problems.iterator().next().getProblem();
    then(problem.getDescription());
    then(problem.getDescription()).contains("Commit Status Publisher");
    then(problem.getDescription()).contains("buildFinished");
    then(problem.getDescription()).contains(PUBLISHER_ERROR);
    then(problem.getDescription()).contains(myPublisher.getId());
  }

  public void should_handle_async_errors() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
  }

  public void should_retain_all_errors_for_multiple_roots() {
    prepareVcs("vcs1", "111", "rev1_2", false);
    prepareVcs("vcs2", "222", "rev2_2", false);
    prepareVcs("vcs3", "333", "rev3_2", false);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger); // This problem should be cleaned during buildFinished(...)
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myPublisher.shouldReportError();
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild); // three problems should be reported here
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger); // and one more - later
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(4); // Must be 4 in total, neither 1 nor 5
  }


  public void should_not_publish_failure_if_marked_successful() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(runningBuild, Status.FAILURE, Status.NORMAL);
    then(myPublisher.isFailureReceived()).isFalse();
  }

  public void should_not_publish_if_failed_to_collect_changes() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    BuildPromotionImpl promotion = (BuildPromotionImpl) runningBuild.getBuildPromotion();
    promotion.setAttribute(COLLECT_CHANGES_ERROR, "Bad VCS root");
    myListener.buildChangedStatus(runningBuild, Status.NORMAL, Status.FAILURE);
    then(myPublisher.isFailureReceived()).isFalse();
  }

  @TestFor(issues = "TW-47724")
  public void should_not_publish_status_for_personal_builds() throws IOException {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startPersonalBuild(myFixture.createUserAccount("newuser"), myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    then(myPublisher.isFinishedReceived()).isFalse();
    then(myPublisher.isSuccessReceived()).isFalse();
  }

  private void prepareVcs() {
   prepareVcs("vcs1", "111", "rev1_2", true);
  }

  private void prepareVcs(String vcsRootName, String currentVersion, String revNo, boolean setVcsRootIdParam) {
    final SVcsRoot vcsRoot = myFixture.addVcsRoot("svn", vcsRootName);
    if (setVcsRootIdParam) {
      myPublisher.setVcsRootId(vcsRoot.getExternalId());
    }
    myBuildType.addVcsRoot(vcsRoot);
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    myCurrentVersions.put(vcsRoot.getName(), currentVersion);
    myFixture.addModification(new ModificationData(new Date(),
            Collections.singletonList(new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file","1", "2")),
            "descr2", "user", vcsRootInstance, revNo, revNo));
  }

  private class MockPublisherRegisterFailure extends MockPublisher {

    private int myFailuresReceived = 0;
    private int myFinishedReceived = 0;
    private int mySuccessReceived = 0;

    private boolean myShouldThrowException = false;
    private boolean myShouldReportError = false;

    MockPublisherRegisterFailure(SBuildType buildType, CommitStatusPublisherProblems problems) {
      super(myPublisherSettings, PUBLISHER_ID, buildType, myFeatureDescriptor.getId(), Collections.<String, String>emptyMap(), problems);
    }

    boolean isFailureReceived() { return myFailuresReceived > 0; }
    boolean isFinishedReceived() { return myFinishedReceived > 0; }
    boolean isSuccessReceived() { return mySuccessReceived > 0; }

    int failuresReceived() { return myFailuresReceived; }

    int finishedReceived() { return myFinishedReceived; }

    int successReceived() { return mySuccessReceived; }

    void shouldThrowException() {myShouldThrowException = true; }
    void shouldReportError() {myShouldReportError = true; }

    @Override
    public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
      myFinishedReceived++;
      Status s = build.getBuildStatus();
      if (s.equals(Status.NORMAL)) mySuccessReceived++;
      if (s.equals(Status.FAILURE)) myFailuresReceived++;
      if (myShouldThrowException) {
        throw new PublisherException(PUBLISHER_ERROR);
      } else if (myShouldReportError) {
        myProblems.reportProblem(this, "My build", null, null, myLogger);
      }
      return true;
    }

    @Override
    public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
      myFailuresReceived++;
      return true;
    }

    @Override
    public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
      return super.buildMarkedAsSuccessful(build, revision, buildInProgress);
    }

  }
}
