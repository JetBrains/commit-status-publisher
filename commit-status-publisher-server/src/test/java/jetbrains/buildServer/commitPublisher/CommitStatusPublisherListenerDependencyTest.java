package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerDependencyTest extends CommitStatusPublisherTestBase {
  private BuildTypeAndPublisher myBuildTypeAndPublisher0;
  private BuildTypeAndPublisher myBuildTypeAndPublisher1;
  private PublisherLogger myLogger;
  private PublisherManagerFacade myPublisherManagerFacade;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new PublisherLogger();
    myPublisherManagerFacade = new PublisherManagerFacade();

    myBuildTypeAndPublisher0 = getBuildConfiguration("0");
    myBuildTypeAndPublisher1 = getBuildConfiguration("1");

    final PublisherManager myPublisherManager = myPublisherManagerFacade.getPublisherManager();

    final BuildHistory history = myFixture.getHistory();
    new CommitStatusPublisherListener(getEventDispatcher(), myPublisherManager, history, myRBManager, myProblems);

    addDependency(myBuildTypeAndPublisher1.getBuildType(), myBuildTypeAndPublisher0.getBuildType());
  }

  @Test(dataProvider = "configuration")
  public void build_with_dependency_properly_reported(Boolean configParameter, int publisher0messages, int publisher1messages) {
    if (configParameter != null) {
      myBuildTypeAndPublisher1.getBuildType().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishToDependencies", configParameter.toString()));
    }
    startAndFinishBuild();

    final MockPublisher publisher0 = myBuildTypeAndPublisher0.getPublisher();
    final MockPublisher publisher1 = myBuildTypeAndPublisher1.getPublisher();

    then(publisher0.queuedReceived()).isEqualTo(publisher0messages);
    then(publisher0.finishedReceived()).isEqualTo(publisher0messages);

    then(publisher1.queuedReceived()).isEqualTo(publisher1messages);
    then(publisher1.finishedReceived()).isEqualTo(publisher1messages);
  }

  @DataProvider(name = "configuration")
  public static Object[][] configuration() {
    return new Object[][]{
      new Object[]{null, 1, 1},  // no option defined, not published to dependency
      new Object[]{true, 2, 1},  // option with 'true' value, published to dependency
      new Object[]{false, 1, 1}, // option with 'false' value, not published to dependency
    };
  }

  private BuildTypeAndPublisher getBuildConfiguration(final String index) {
    final BuildTypeImpl buildType = registerBuildType("buildConf" + index, "MyDefaultTestProject");

    final SVcsRoot vcsRoot = myFixture.addVcsRoot("jetbrains.git", "vcs" + index, buildType);

    final SBuildFeatureDescriptor feature = buildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, Collections.singletonMap(Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID + index));

    final MockPublisherSettings publisherSettings = new MockPublisherSettingsEx(MockPublisherSettings.PUBLISHER_ID + index);

    myPublisherManagerFacade.getMyPublisherSettings().add(publisherSettings);

    final MockPublisher publisher = new MockPublisher(publisherSettings, MockPublisherSettings.PUBLISHER_ID + index, buildType, feature.getId(), Collections.<String, String>emptyMap(), myProblems, myLogger);

    publisherSettings.setPublisher(publisher);

    prepareVcs(vcsRoot, index, "rev1_2", SetVcsRootIdMode.EXT_ID, buildType, publisher);

    return new BuildTypeAndPublisher(buildType, publisher);
  }

  private void startAndFinishBuild() {
    final BuildPromotion build1 = build().in(myBuildTypeAndPublisher1.getBuildType()).addToQueue().getBuildPromotion();
    final SRunningBuild runningBuild1 = BuildBuilder.run(build1.getQueuedBuild(), myFixture);
    finishBuild(runningBuild1, false);
  }

  private class BuildTypeAndPublisher {
    private final BuildTypeImpl myBuildType;
    private final MockPublisher myPublisher;

    public BuildTypeAndPublisher(BuildTypeImpl myBuildType, MockPublisher myPublisher) {
      this.myBuildType = myBuildType;
      this.myPublisher = myPublisher;
    }

    public MockPublisher getPublisher() {
      return myPublisher;
    }

    public BuildTypeImpl getBuildType() {
      return myBuildType;
    }
  }

  private class MockPublisherSettingsEx extends MockPublisherSettings {
    final String myPublisherId;

    public MockPublisherSettingsEx(final String publisherId) {
      super(myProblems);
      myPublisherId = publisherId;
    }

    @NotNull
    @Override
    public String getId() {
      return myPublisherId;
    }
  }

  private class PublisherManagerFacade {
    private final Collection<CommitStatusPublisherSettings> myPublisherSettings;

    public PublisherManagerFacade() {
      myPublisherSettings = new ArrayList<CommitStatusPublisherSettings>();
    }

    public Collection<CommitStatusPublisherSettings> getMyPublisherSettings() {
      return myPublisherSettings;
    }

    public PublisherManager getPublisherManager() {
      return new PublisherManager(myPublisherSettings);
    }
  }
}
