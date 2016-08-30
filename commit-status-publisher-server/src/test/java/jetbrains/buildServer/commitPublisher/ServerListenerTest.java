package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BaseJMockTestCase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

/**
 * @author anton.zamolotskikh, 30/08/16.
 */

@Test
public class ServerListenerTest extends BaseJMockTestCase {

  private TestEventDispatcher myDispatcher;
  private SVcsRoot myVcsRoot;
  private SProject myProject;
  private SBuildType myBT;
  private SBuildFeatureDescriptor myFeatureDescriptor;

  private static final String MY_VCS_ID = "MY_VCS_ID";

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myDispatcher = new TestEventDispatcher();
    new ServerListener(myDispatcher);
    myVcsRoot = m.mock(SVcsRoot.class);
    myProject = m.mock(SProject.class);
    myBT = m.mock(SBuildType.class);
    myFeatureDescriptor = new FakeFeatureDescriptor();

    myFeatureDescriptor.getParameters().put(Constants.VCS_ROOT_ID_PARAM, MY_VCS_ID);
  }

  public void must_change_vcs_root_external_id_if_renamed() {
    List<ConfigActionsServerListener> listeners = myDispatcher.getListeners();
    assertEquals(1, listeners.size());
    ConfigActionsServerListener casl = listeners.get(0);

    ConfigAction cfgAction = m.mock(ConfigAction.class);

    m.checking(new Expectations() {{
      oneOf(myVcsRoot).getProject();
      will(returnValue(myProject));

      oneOf(myProject).getBuildTypes();
      will(returnValue(Collections.singletonList(myBT)));

      oneOf(myBT).getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE);
      will(returnValue(Collections.singletonList(myFeatureDescriptor)));

      oneOf(myBT).updateBuildFeature(myFeatureDescriptor.getId(), CommitStatusPublisherFeature.TYPE,
              new HashMap<String, String>() {{
                put(Constants.VCS_ROOT_ID_PARAM, MY_VCS_ID + "_MODIFIED");
              }});

      oneOf(myProject).getBuildTypeTemplates();

      oneOf(myBT).persist(with(any(ConfigAction.class)));
    }});

    casl.vcsRootExternalIdChanged(cfgAction, myVcsRoot, MY_VCS_ID, MY_VCS_ID + "_MODIFIED");

    m.assertIsSatisfied();
  }


  public void must_change_vcs_root_external_id_if_copied() {
    List<ConfigActionsServerListener> listeners = myDispatcher.getListeners();
    assertEquals(1, listeners.size());
    ConfigActionsServerListener casl = listeners.get(0);

    assertTrue(casl instanceof CustomSettingsMapper);
    CustomSettingsMapper csm = (CustomSettingsMapper) casl;

    final CopiedObjects copiedObjects = m.mock(CopiedObjects.class);

    final SVcsRoot vcsRootCopy = m.mock(SVcsRoot.class, "VcsRootCopy");
    final SBuildType btCopy = m.mock(SBuildType.class, "BTCopy");

    m.checking(new Expectations() {{

      oneOf(copiedObjects).getCopiedVcsRootsMap();
      will(returnValue(new HashMap<SVcsRoot, SVcsRoot>() {{
        put(myVcsRoot, vcsRootCopy);
      }}));

      oneOf(copiedObjects).getCopiedSettingsMap();
      will(returnValue(new HashMap<BuildTypeSettings, BuildTypeSettings>() {{
        put(myBT, btCopy);
      }}));

      oneOf(myVcsRoot).getExternalId();
      will(returnValue(MY_VCS_ID));
      oneOf(vcsRootCopy).getExternalId();
      will(returnValue(MY_VCS_ID + "_COPIED"));

      oneOf(btCopy).getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE);
      will(returnValue(Collections.singletonList(myFeatureDescriptor)));

      oneOf(btCopy).updateBuildFeature(myFeatureDescriptor.getId(), CommitStatusPublisherFeature.TYPE,
              new HashMap<String, String>() {{
                put(Constants.VCS_ROOT_ID_PARAM, MY_VCS_ID + "_COPIED");
              }});
    }});

    csm.mapData(copiedObjects);

    m.assertIsSatisfied();
  }


  private class TestEventDispatcher extends EventDispatcher<ConfigActionsServerListener> {
    TestEventDispatcher() {
      super(ConfigActionsServerListener.class);
    }
  }

  private class FakeFeatureDescriptor implements SBuildFeatureDescriptor {
    private final Map<String, String> myParams = new HashMap<String, String>();

    @NotNull
    public String getId() {
      return "TEST_FEATURE_1";
    }

    @NotNull
    public String getType() {
      return CommitStatusPublisherFeature.TYPE;
    }

    @NotNull
    public Map<String, String> getParameters() {
      return myParams;
    }

    @NotNull
    public BuildFeature getBuildFeature() {
      throw new RuntimeException("Not allowed");
    }
  }


}
