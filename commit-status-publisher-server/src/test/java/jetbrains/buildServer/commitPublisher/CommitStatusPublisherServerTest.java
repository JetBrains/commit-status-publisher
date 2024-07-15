package jetbrains.buildServer.commitPublisher;

import java.util.Map;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.assertj.core.api.BDDAssertions;
import org.testng.annotations.Test;


@Test
public abstract class CommitStatusPublisherServerTest extends CommitStatusPublisherTest {
  public void test_should_not_publish_queued_over_final_status() throws Exception {
    if (isSkipEvent(EventToTest.QUEUED)) return;

    setInternalProperty("teamcity.commitStatusPublisher.statusCache.wildcardTtl", 0); // disable status caching when there were no statuses returned

    new CommitStatusPublisherListener(myFixture.getEventDispatcher(), new PublisherManager(myServer), myFixture.getHistory(), myFixture.getBuildsManager(), myFixture.getBuildPromotionManager(), myProblems,
                                      myFixture.getServerResponsibility(), myFixture.getSingletonService(ExecutorServices.class),
                                      myFixture.getSingletonService(ProjectManager.class), myFixture.getSingletonService(TeamCityNodes.class),
                                      myFixture.getSingletonService(UserModel.class), myFixture.getMultiNodeTasks());

    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().stream().filter(root -> root.getParent().getId() == myVcsRoot.getId()).findFirst().get();
    setUpFeature();

    addToQueueWithRevision(vcsRootInstance, "2");
    waitFor(() -> checkLastEventQueued());

    SRunningBuild build = myFixture.flushQueueAndWait();
    waitFor(() -> checkLastEventStarted());

    myFixture.finishBuild(build, false);
    waitFor(() -> checkLastEventFinished(true));

    addToQueueWithRevision(vcsRootInstance, "2");
    checkLastEventFinished(true);

    SRunningBuild build2 = myFixture.flushQueueAndWait();
    waitFor(() -> checkLastEventStarted());

    myFixture.finishBuild(build2, false);
    waitFor(() -> checkLastEventFinished(true));

    BDDAssertions.then(getAllRequestsAsString().stream().filter(request -> checkEventQueued(request)).count()).isEqualTo(1);
  }

  protected void setUpFeature() {

    Map<String, String> featureParams = getPublisherParams();
    featureParams.put(Constants.PUBLISHER_ID_PARAM, myPublisher.getId());
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, featureParams);
    myServer.registerExtension(CommitStatusPublisherSettings.class, "test", myPublisherSettings);

    PublisherManager publisherManager = new PublisherManager(myServer);
    WebControllerManager wcm = new CommitStatusPublisherTestBase.MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    PublisherSettingsController settingsController = new PublisherSettingsController(wcm, pluginDescr, publisherManager, myProjectManager, myFixture.getSingletonService(
      SecurityContext.class));
    CommitStatusPublisherFeatureController featureController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, publisherManager, settingsController, myFixture.getSecurityContext());
    CommitStatusPublisherFeature feature = new CommitStatusPublisherFeature(featureController, publisherManager);
    myServer.registerExtension(BuildFeature.class, CommitStatusPublisherFeature.TYPE, feature);
  }
}
