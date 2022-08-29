package jetbrains.buildServer.swarm.web;

import java.util.HashMap;
import jetbrains.buildServer.BaseWebTestCase;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.swarm.SwarmClientManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.swarm.web.SwarmBuildPageExtension.SWARM_REVIEWS_ENABLED;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class SwarmBuildPageExtensionTest extends BaseWebTestCase {

  private SwarmBuildPageExtension myExtension;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myExtension = new SwarmBuildPageExtension(myServer, myWebManager, new MockPluginDescriptor(), new SwarmClientManager(myWebLinks, () -> null));

    setInternalProperty(SWARM_REVIEWS_ENABLED, "true");
  }

  @Test
  public void should_not_be_available_without_swarm_feature() throws Exception {
    SFinishedBuild build = createBuild(Status.NORMAL);
    MockRequest buildRequest = new MockRequest("buildId", String.valueOf(build.getBuildId()));
    then(myExtension.isAvailable(buildRequest)).isFalse();

    HashMap<String, Object> model = new HashMap<>();
    myExtension.fillModel(model, buildRequest, build);
    
    then(((SwarmBuildDataBean)model.get(SwarmBuildPageExtension.SWARM_BEAN)).isEmpty()).isTrue();
  }
}
