package jetbrains.buildServer.swarm;

import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature.TYPE;
import static jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings.PARAM_URL;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class SwarmClientManagerTest extends BaseServerTestCase {

  private SwarmClientManager mySwarmClientManager;
  private SVcsRootImpl myRoot;


  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySwarmClientManager = new SwarmClientManager(myWebLinks, () -> null);

    CommitStatusPublisherFeature publisherFeature = Mockito.mock(CommitStatusPublisherFeature.class);
    Mockito.when(publisherFeature.getType()).thenReturn(TYPE);
    Mockito.when(publisherFeature.isMultipleFeaturesPerBuildTypeAllowed()).thenReturn(true);

    myServer.registerExtension(BuildFeature.class, "commitPublisherTest", publisherFeature);

    myRoot = myFixture.addVcsRoot("perforce", "");
  }

  @Test
  public void should_create_swarm_client() throws Exception {
    SwarmClient swarmClient = mySwarmClientManager.getSwarmClient(Collections.singletonMap(PARAM_URL, "http://swarm"));
    then(swarmClient.getSwarmServerUrl()).isEqualTo("http://swarm");
  }

  @Test
  public void should_create_swarm_client_from_feature() throws Exception {

    myBuildType.addBuildFeature(TYPE, new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID);
      put(Constants.VCS_ROOT_ID_PARAM, myRoot.getExternalId());
      put(PARAM_URL, "http://swarm1");
    }});

    SwarmClient client = getSwarmClient();
    then(client).isNotNull();
    then(client.getSwarmServerUrl()).isEqualTo("http://swarm1");
  }

  private SwarmClient getSwarmClient() {
    return mySwarmClientManager.getSwarmClient(myBuildType, myBuildType.getVcsRootInstanceForParent(myRoot));
  }

  @Test
  public void should_create_swarm_client_from_feature_with_no_explicit_root() throws Exception {

    myBuildType.addBuildFeature(TYPE, new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID);
      put(PARAM_URL, "http://swarm1");
    }});

    SwarmClient client = getSwarmClient();
    then(client).isNotNull();
    then(client.getSwarmServerUrl()).isEqualTo("http://swarm1");
  }

  @Test
  public void should_not_create_swarm_client_from_another_publisher() throws Exception {

    myBuildType.addBuildFeature(TYPE, new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, "mock-publisher-id");
      put(PARAM_URL, "http://swarm1");
    }});

    SwarmClient client = getSwarmClient();
    then(client).isNull();
  }

  @Test
  public void should_not_create_swarm_client_from_disabled_feature() throws Exception {

    SBuildFeatureDescriptor featureDescriptor = myBuildType.addBuildFeature(TYPE, new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID);
      put(PARAM_URL, "http://swarm1");
    }});
    myBuildType.setEnabled(featureDescriptor.getId(), false);

    SwarmClient client = getSwarmClient();
    then(client).isNull();
  }

  @Test
  public void should_create_swarm_client_from_different_roots() throws Exception {

    myBuildType.addBuildFeature(TYPE, new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID);
      put(PARAM_URL, "http://swarm2");
      put(Constants.VCS_ROOT_ID_PARAM, myRoot.getExternalId());
    }});

    SVcsRootImpl root2 = myFixture.addVcsRoot("perforce", "root2");
    myBuildType.addBuildFeature(TYPE, new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID);
      put(PARAM_URL, "http://swarm1");
      put(Constants.VCS_ROOT_ID_PARAM, root2.getExternalId());
    }});

    SwarmClient client = getSwarmClient();
    then(client.getSwarmServerUrl()).isEqualTo("http://swarm2");

    SwarmClient swarmClient2 = mySwarmClientManager.getSwarmClient(myBuildType, myBuildType.getVcsRootInstanceForParent(root2));
    then(swarmClient2.getSwarmServerUrl()).isEqualTo("http://swarm1");
  }

  @Test
  public void should_reuse_swarm_clients() throws Exception {
    SwarmClient c1 = mySwarmClientManager.getSwarmClient(new HashMap<String, String>() {{
      put("foo", "bar");
    }});
    SwarmClient c2 = mySwarmClientManager.getSwarmClient(new TreeMap<String, String>() {{
      put("foo", "bar");
    }});

    assertSame(c1, c2);

    SwarmClient c3 = mySwarmClientManager.getSwarmClient(new HashMap<String, String>() {{
      put("foo", "bar3");
    }});
    assertNotSame(c1, c3);

  }

}
