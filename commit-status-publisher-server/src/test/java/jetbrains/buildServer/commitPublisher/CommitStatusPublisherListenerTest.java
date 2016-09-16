package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private static final String PUBLISHER_ID = "MockPublisherId";

  private CommitStatusPublisherListener myListener;
  private MockPublisherRegisterFailure myPublisher;
  private BuildRevision myRevision;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final PublisherManager myPublisherManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>singletonList(new CommitStatusPublisherListenerTest.MockPublisherSettings()));
    final BuildHistory history = m.mock(BuildHistory.class);
    myListener = new CommitStatusPublisherListener(EventDispatcher.create(BuildServerListener.class), myPublisherManager, history, myRBManager, myProblems);
    myPublisher = new MockPublisherRegisterFailure();

    final VcsRootInstance vcsRootInstance = m.mock(VcsRootInstance.class);
    final SVcsRoot vcsRoot = m.mock(SVcsRoot.class);
    myRevision = new BuildRevision(vcsRootInstance, "revision1", "*", "Revision");

    Expectations genericExpectations = new Expectations() {{

      allowing(vcsRootInstance).describe(true); will(returnValue("VCS Root Instance"));
      allowing(vcsRootInstance).getParent(); will(returnValue(vcsRoot));

      allowing(vcsRoot).getExternalId(); will(returnValue(myPublisher.getVcsRootId()));

      allowing(myFeatureDescriptor).getParameters();
      will(returnValue(new HashMap<String, String>() {{
        put(Constants.PUBLISHER_ID_PARAM, myPublisher.getId());
      }}));
    }};

    m.checking(genericExpectations);
  }

  public void should_publish_failure() {
    mockChangesCollected();

    myListener.buildChangedStatus(myRunningBuild, Status.NORMAL, Status.FAILURE);

    then(myPublisher.isFailureReceived()).isTrue();
  }

  public void should_not_publish_failure_if_marked_successful() {
    mockChangesCollected();

    myListener.buildChangedStatus(myRunningBuild, Status.FAILURE, Status.NORMAL);

    then(myPublisher.isFailureReceived()).isFalse();
  }

  private void mockChangesCollected() {
    m.checking(new Expectations() {{
      allowing(myBuildPromotion).isFailedToCollectChanges();
      will(returnValue(false));

      allowing(myRunningBuild).getRevisions(); will(returnValue(Collections.singletonList(myRevision)));
    }});
  }

  public void should_not_publish_if_failed_to_collect_changes() {
    m.checking(new Expectations() {{
      allowing(myBuildPromotion).isFailedToCollectChanges();
      will(returnValue(true));

      never(myRunningBuild).getRevisions();
    }});
    myListener.buildChangedStatus(myRunningBuild, Status.NORMAL, Status.FAILURE);

    then(myPublisher.isFailureReceived()).isFalse();
  }

  private class MockPublisherRegisterFailure extends MockPublisher {

    private boolean myFailureReceived = false;

    MockPublisherRegisterFailure() {
      super(PUBLISHER_ID);
    }

    boolean isFailureReceived() { return myFailureReceived; }

    @Override
    public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
      myFailureReceived = true;
      return true;
    }
  }


  private class MockPublisherSettings extends DummyPublisherSettings {
    @Override
    @NotNull
    public String getId() {
      return PUBLISHER_ID;
    }

    @Override
    public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
      return myPublisher;
    }
  }
}
