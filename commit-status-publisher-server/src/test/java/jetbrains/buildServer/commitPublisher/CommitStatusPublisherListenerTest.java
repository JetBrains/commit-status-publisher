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
  private MockPublisherRegisterFailure myPublisher;
  private VcsRootInstance myVcsRootInstance;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final PublisherManager myPublisherManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>singletonList(new CommitStatusPublisherListenerTest.MockPublisherSettings()));
    final BuildHistory history = myFixture.getHistory();
    myListener = new CommitStatusPublisherListener(EventDispatcher.create(BuildServerListener.class), myPublisherManager, history, myRBManager, myProblems);
    myPublisher = new MockPublisherRegisterFailure(myBuildType, myProblems);
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

  public void should_handle_publisher_error() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myPublisher.shouldRaiseError();
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
    final SVcsRoot vcsRoot = myFixture.addVcsRoot("svn", "vcs1");
    myPublisher.setVcsRootId(vcsRoot.getExternalId());
    myBuildType.addVcsRoot(vcsRoot);
    myVcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    myCurrentVersion = "111";
    myFixture.addModification(new ModificationData(new Date(),
            Collections.singletonList(new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file","1", "2")),
            "descr2", "user", myVcsRootInstance, "rev2", "rev2"));
  }

  private class MockPublisherRegisterFailure extends MockPublisher {

    private boolean myFailureReceived = false;
    private boolean myFinishedReceived = false;
    private boolean mySuccessReceived = false;

    private boolean myShouldRiseError = false;

    MockPublisherRegisterFailure(SBuildType buildType, CommitStatusPublisherProblems problems) {
      super(PUBLISHER_ID, buildType, myFeatureDescriptor.getId(), Collections.<String, String>emptyMap(), problems);
    }

    boolean isFailureReceived() { return myFailureReceived; }
    boolean isFinishedReceived() { return myFinishedReceived; }
    boolean isSuccessReceived() { return mySuccessReceived; }

    void shouldRaiseError() { myShouldRiseError = true; }

    @Override
    public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
      myFinishedReceived = true;
      Status s = build.getBuildStatus();
      if (s.equals(Status.NORMAL)) mySuccessReceived = true;
      if (s.equals(Status.FAILURE)) myFailureReceived = true;
      if (myShouldRiseError) {
        throw new PublisherException(PUBLISHER_ERROR);
      }
      return true;
    }

    @Override
    public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
      myFailureReceived = true;
      return true;
    }

    @Override
    public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
      return super.buildMarkedAsSuccessful(build, revision, buildInProgress);
    }

  }


  private class MockPublisherSettings extends DummyPublisherSettings {
    @Override
    @NotNull
    public String getId() {
      return PUBLISHER_ID;
    }

    @Override
    public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
      return myPublisher;
    }
  }
}
