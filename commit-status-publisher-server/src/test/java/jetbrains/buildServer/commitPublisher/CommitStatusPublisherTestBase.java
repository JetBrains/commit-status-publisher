package jetbrains.buildServer.commitPublisher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.MultipleRunnersBean;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.web.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.mvc.Controller;

/**
 * @author anton.zamolotskikh, 15/09/16.
 */
public class CommitStatusPublisherTestBase extends BaseServerTestCase {

  protected PublisherSettingsController mySettingsController;
  protected CommitStatusPublisherFeatureController myFeatureController;
  protected VcsManagerEx myVcsManager;
  protected ProjectManager myProjectManager;
  protected SBuildFeatureDescriptor myFeatureDescriptor;
  protected RunningBuildsManager myRBManager;
  protected MockPublisherSettings myPublisherSettings;
  protected CommitStatusPublisherProblems myProblems;
  protected CommitStatusPublisherFeature myFeature;
  protected Map<String, String> myCurrentVersions;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;

  protected void setUp() throws Exception {
    super.setUp();
    WebControllerManager wcm = new MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    myProjectManager = myFixture.getProjectManager();
    myPublisherSettings = new MockPublisherSettings(myProblems);
    final PublisherManager publisherManager = new PublisherManager(Collections.singletonList((CommitStatusPublisherSettings) myPublisherSettings));

    myCurrentVersions = new HashMap<String, String>();
    mySettingsController = new PublisherSettingsController(wcm, pluginDescr, publisherManager, myProjectManager);

    myFeatureController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, publisherManager, mySettingsController);
    myVcsManager = myFixture.getVcsManager();

    ServerVcsSupport vcsSupport = new MockVcsSupport("jetbrains.git") {

      private final Map<String, String> myVersions = myCurrentVersions;

      @Override
      @NotNull
      public String getCurrentVersion(@NotNull final VcsRoot root) {
        if (!myVersions.containsKey(root.getName())) {
          throw new IllegalArgumentException("Unknown VCS root");
        }
        return myCurrentVersions.get(root.getName());
      }
    };
    myVcsManager.registerVcsSupport(vcsSupport);

    myFeatureDescriptor = myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, Collections.singletonMap(Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID));
    myFeature = new CommitStatusPublisherFeature(myFeatureController, publisherManager);
    myRBManager = myFixture.getSingletonService(RunningBuildsManager.class);
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myProblemNotificationEngine);
    ExtensionHolder extensionHolder = myFixture.getSingletonService(ExtensionHolder.class);
    extensionHolder.registerExtension(BuildFeature.class, CommitStatusPublisherFeature.TYPE, myFeature);
  }




  private class MockWebControllerManager implements WebControllerManager {

    @Override
    public void registerController(@NotNull String s, @NotNull Controller controller) { }

    @Override
    public void registerAction(@NotNull BaseController baseController, @NotNull ControllerAction controllerAction) { }

    @Nullable
    @Override
    public ControllerAction getAction(@NotNull BaseController baseController, @NotNull HttpServletRequest httpServletRequest) {
      return null;
    }

    @Override
    public void addPageExtension(WebPlace webPlace, WebExtension webExtension) { }

    @Override
    public void removePageExtension(WebPlace webPlace, WebExtension webExtension) { }

    @NotNull
    @Override
    public PagePlace getPlaceById(@NotNull PlaceId placeId) {
      return null;
    }
  }

  class MockBuildTypeForm extends BuildTypeForm {
    private final VcsSettingsBean myVcsBean;

    MockBuildTypeForm(SBuildType buildType, VcsSettingsBean bean) {
      super(buildType.getProject());
      myVcsBean = bean;
    }

    @NotNull
    @Override
    protected MultipleRunnersBean createMultipleRunnersBean() {
      return null;
    }

    @NotNull
    @Override
    public VcsSettingsBean getVcsRootsBean() {
      return myVcsBean;
    }

    @NotNull
    @Override
    public ValueResolver getValueResolver() {
      return null;
    }

    @Override
    public boolean isBranchesConfigured() {
      return false;
    }

    @Override
    public boolean isCompositeBuild() {
      return false;
    }
  }

  class MockVcsSettingsBean extends VcsSettingsBean {


    MockVcsSettingsBean(@NotNull SProject project, @NotNull BuildTypeSettings buildTypeSettings, @NotNull VcsManager vcsManager, @NotNull ProjectManager projectManager) {
      super(project, buildTypeSettings, vcsManager, projectManager);
    }

    @Override
    protected boolean supportsLabeling(SVcsRoot sVcsRoot) {
      return false;
    }

    @Nullable
    @Override
    public Browser getVcsBrowser(boolean b, @Nullable String s) {
      return null;
    }

    @Override
    public boolean isDefaultExcluded() { return false; }
  }


}
