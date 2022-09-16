package jetbrains.buildServer.swarm.web;

import java.util.Date;
import java.util.HashMap;
import jetbrains.buildServer.BaseWebTestCase;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.serverSide.impl.MockVcsModification;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.swarm.SwarmTestUtil;
import jetbrains.buildServer.util.cache.ResetCacheRegisterImpl;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class SwarmChangeLinkExtensionTest extends BaseWebTestCase {

  public static final String SWARM_ROOT = "http://swarm-root/";
  private SwarmChangeLinkExtension myExtension;
  private VcsRootInstance myVri;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    SwarmTestUtil.addSwarmFeature(myBuildType, SWARM_ROOT);

    SVcsRootImpl perforce = myFixture.addVcsRoot("perforce", "");
    myVri = myBuildType.getVcsRootInstanceForParent(perforce);
    SwarmClientManager swarmClientManager = new SwarmClientManager(myWebLinks, () -> null, new ResetCacheRegisterImpl());

    myExtension = new SwarmChangeLinkExtension(myWebManager, swarmClientManager, myProjectManager);
  }

  @Test
  public void should_be_available_with_swarm_feature() throws Exception {
    MockRequest request = prepareRequest();
    then(myExtension.isAvailable(request)).isTrue();
  }

  @Test
  public void should_not_be_available_without_swarm_feature() throws Exception {
    myBuildType.removeBuildFeature(myBuildType.getBuildFeatures().iterator().next().getId());

    MockRequest request = prepareRequest();
    then(myExtension.isAvailable(request)).isFalse();
  }

  @Test
  public void should_not_be_available_not_perforce_change_feature() throws Exception {
    SVcsRootImpl svnRoot = myFixture.addVcsRoot("svn", "");
    MockRequest request = new MockRequest();
    MockVcsModification modification = new MockVcsModification("kir", "desc", new Date(), "2233");
    modification.setRoot(myBuildType.getVcsRootInstanceForParent(svnRoot));

    request.setAttribute("modification", modification);
    request.setAttribute("buildType", myBuildType);
    then(myExtension.isAvailable(request)).isFalse();
  }

  @Test
  public void should_provide_swarm_link_ordinary_change() throws Exception {
    MockRequest request = prepareRequest();

    HashMap<String, Object> model = new HashMap<>();
    myExtension.fillModel(model, request);

    then(model)
      .containsEntry("swarmChangeUrl", SWARM_ROOT + "changes/1234")
      .containsEntry("showType", "compact");
  }

  @NotNull
  private MockRequest prepareRequest() {
    MockRequest request = new MockRequest();
    MockVcsModification modification = new MockVcsModification("kir", "desc", new Date(), "1234");
    modification.setRoot(myVri);

    request.setAttribute("modification", modification);
    request.setAttribute("buildType", myBuildType);
    return request;
  }

}
