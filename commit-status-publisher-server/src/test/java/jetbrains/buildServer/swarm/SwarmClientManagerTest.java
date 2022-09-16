package jetbrains.buildServer.swarm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import jetbrains.buildServer.util.cache.ResetCacheRegisterImpl;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    mySwarmClientManager = new SwarmClientManager(myWebLinks, () -> null, new ResetCacheRegisterImpl());

    CommitStatusPublisherFeature publisherFeature = Mockito.mock(CommitStatusPublisherFeature.class);
    Mockito.when(publisherFeature.getType()).thenReturn(TYPE);
    Mockito.when(publisherFeature.isMultipleFeaturesPerBuildTypeAllowed()).thenReturn(true);

    myServer.registerExtension(BuildFeature.class, "commitPublisherTest", publisherFeature);

    myRoot = myFixture.addVcsRoot("perforce", "");
  }

  @Test
  public void should_create_swarm_client_from_params() throws Exception {
    SwarmClient swarmClient = mySwarmClientManager.getSwarmClient(Collections.singletonMap(PARAM_URL, "http://swarm"));
    then(swarmClient.getSwarmServerUrl()).isEqualTo("http://swarm");
  }

  @Test
  public void should_create_swarm_client_from_feature() throws Exception {
    addSwarmFeature(myRoot.getExternalId(), "http://swarm1");
    checkClientCreatedOK();
  }

  @Test
  public void should_create_swarm_client_from_feature_with_no_explicit_root() throws Exception {
    addSwarmFeature(null, "http://swarm1");
    checkClientCreatedOK();
  }

  @Test
  public void should_not_create_swarm_client_from_feature_wrong_root() throws Exception {
    addSwarmFeature("AnotherRootId", "http://swarm1");
    then(getSwarmClient()).isNull();
  }

  public void should_not_create_swarm_client_from_feature_non_p4_root() throws Exception {
    myBuildType.removeVcsRoot(myRoot);
    myRoot = myFixture.addVcsRoot("svn", "");

    addSwarmFeature(null, "http://swarm1");

    then(getSwarmClient()).isNull();
  }

  public void should_create_swarm_clients_several_roots_one_feature() throws Exception {
    SVcsRootImpl root2 = myFixture.addVcsRoot("perforce", "");
    addSwarmFeature(null, "http://swarm1");

    then(getSwarmClient()).isNotNull();
    then(getSwarmClient(root2)).isNotNull();
  }

  private void checkClientCreatedOK() {
    SwarmClient client = getSwarmClient();
    then(client).isNotNull();
    then(client.getSwarmServerUrl()).isEqualTo("http://swarm1");
  }

  private SwarmClient getSwarmClient() {
    return getSwarmClient(myRoot);
  }

  private SwarmClient getSwarmClient(@NotNull SVcsRoot root) {
    return mySwarmClientManager.getSwarmClient(myBuildType, Objects.requireNonNull(myBuildType.getVcsRootInstanceForParent(root)));
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

    SBuildFeatureDescriptor featureDescriptor = SwarmTestUtil.addSwarmFeature(myBuildType, "http://swarm1");

    myBuildType.setEnabled(featureDescriptor.getId(), false);

    SwarmClient client = getSwarmClient();
    then(client).isNull();
  }

  @Test
  public void should_create_swarm_client_from_2_roots_2_features() throws Exception {

    SVcsRootImpl root2 = myFixture.addVcsRoot("perforce", "root2");

    addSwarmFeature(myRoot.getExternalId(), "http://swarm1");
    addSwarmFeature(root2.getExternalId(), "http://swarm2");

    SwarmClient client = getSwarmClient();
    then(client.getSwarmServerUrl()).isEqualTo("http://swarm1");

    then(getSwarmClient(root2).getSwarmServerUrl()).isEqualTo("http://swarm2");
  }

  private void addSwarmFeature(@Nullable final String vcsRootExtId, final String swarmUrl) {
    HashMap<String, String> params = new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID);
      put(PARAM_URL, swarmUrl);
    }};
    if (vcsRootExtId != null) {
      params.put(Constants.VCS_ROOT_ID_PARAM, vcsRootExtId);
    }
    myBuildType.addBuildFeature(TYPE, params);
  }

  public void should_not_create_swarm_client_when_unset_root_2_roots_2_features() throws Exception {

    addSwarmFeature(null, "http://swarm2");

    SVcsRootImpl root2 = myFixture.addVcsRoot("perforce", "root2");
    addSwarmFeature(root2.getExternalId(), "http://swarm1");

    SwarmClient client = getSwarmClient();
    then(client).as("Specific VCS Root is not set when multiple Swarm features present").isNull();

    then(getSwarmClient(root2).getSwarmServerUrl()).as("VCS Root is set").isEqualTo("http://swarm1");
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
