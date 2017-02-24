package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BuildPromotionImpl;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static jetbrains.buildServer.serverSide.BuildAttributes.COLLECT_CHANGES_ERROR;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private CommitStatusPublisherListener myListener;
  private MockPublisher myPublisher;
  private PublisherLogger myLogger;


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new PublisherLogger();
    final PublisherManager myPublisherManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>singletonList(myPublisherSettings));
    final BuildHistory history = myFixture.getHistory();
    myListener = new CommitStatusPublisherListener(EventDispatcher.create(BuildServerListener.class), myPublisherManager, history, myRBManager, myProblems);
    myPublisher = new MockPublisher(myPublisherSettings, MockPublisherSettings.PUBLISHER_ID, myBuildType, myFeatureDescriptor.getId(),
                                    Collections.<String, String>emptyMap(), myProblems, myLogger);
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

  public void should_publish_to_specified_root_with_multiple_roots_attached() {
    prepareVcs("vcs1", "111", "rev1_2", false);
    prepareVcs("vcs2", "222", "rev2_2", true);
    prepareVcs("vcs3", "333", "rev3_2", false);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    then(myPublisher.finishedReceived()).isEqualTo(1);
    then(myPublisher.successReceived()).isEqualTo(1);
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
    then(problem.getDescription()).contains(MockPublisher.PUBLISHER_ERROR);
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
}
