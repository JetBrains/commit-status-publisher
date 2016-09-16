package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BaseJMockTestCase;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.MultipleRunnersBean;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotification;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootEntry;
import jetbrains.buildServer.web.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Collections;

/**
 * @author anton.zamolotskikh, 15/09/16.
 */
public class CommitStatusPublisherTestBase extends BaseJMockTestCase {


  CommitStatusPublisherFeatureController myController;
  SBuildType myBuildType;
  SProject myProject;
  VcsManager myVcsManager;
  ProjectManager myProjectManager;
  PublisherManager myPubManager;
  CommitStatusPublisherFeature myFeature;
  SBuildFeatureDescriptor myFeatureDescriptor;
  SRunningBuild myRunningBuild;
  BuildPromotion myBuildPromotion;
  RunningBuildsManager myRBManager;
  CommitStatusPublisherProblems myProblems;


  protected void setUp() throws Exception {
    super.setUp();
    WebControllerManager wcm = new MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    myProjectManager = m.mock(ProjectManager.class);
    myPubManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>emptyList());
    myController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, myPubManager,
            new PublisherSettingsController(wcm, pluginDescr, myPubManager));
    myBuildType = m.mock(SBuildType.class);
    myProject = m.mock(SProject.class);
    myVcsManager = m.mock(VcsManager.class);
    final ResolvedSettings settings = m.mock(ResolvedSettings.class);
    myFeatureDescriptor = m.mock(SBuildFeatureDescriptor.class);
    myFeature = new CommitStatusPublisherFeature(myController, myPubManager);
    myRunningBuild = m.mock(SRunningBuild.class);
    myBuildPromotion = m.mock(BuildPromotion.class);
    myRBManager = m.mock(RunningBuildsManager.class);
    myProblems = new CommitStatusPublisherProblems(m.mock(SystemProblemNotification.class));


    m.checking(new Expectations() {{
      allowing(myBuildType).getCheckoutType();
      allowing(myBuildType).getCheckoutDirectory();
      allowing(myBuildType).getVcsRootEntries();
      will(returnValue(Collections.<VcsRootEntry>emptyList()));

      allowing(myBuildType).getResolvedSettings();  will(returnValue(settings));
      allowing(myBuildType).getInternalId();  will(returnValue("BT_internal_1"));

      allowing(settings).getBuildFeatures();  will(returnValue(Collections.singletonList(myFeatureDescriptor)));

      allowing(myFeatureDescriptor).getBuildFeature(); will(returnValue(myFeature));
      allowing(myFeatureDescriptor).getId(); will(returnValue("FEATURE_1"));

      allowing(myRunningBuild).getBuildType(); will(returnValue(myBuildType));
      allowing(myRunningBuild).getBuildNumber();  will(returnValue("1"));
      allowing(myRunningBuild).getBuildId();  will(returnValue(1L));
      allowing(myRunningBuild).getBuildTypeExternalId(); will(returnValue("BT1"));
      allowing(myRunningBuild).getBuildPromotion(); will(returnValue(myBuildPromotion));
    }});
  }




  class MockWebControllerManager implements WebControllerManager {

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

  private class MockPluginDescriptor implements PluginDescriptor {
    public String getParameterValue(@NotNull final String key) {
      return null;
    }

    @NotNull
    public String getPluginName() {
      return "";
    }

    @NotNull
    public String getPluginResourcesPath() {
      return "";
    }

    @NotNull
    public String getPluginResourcesPath(@NotNull final String relativePath) {
      return relativePath;
    }

    public String getPluginVersion() {
      return null;
    }

    @NotNull
    public File getPluginRoot() {
      return null;
    }
  }

  class MockBuildTypeForm extends BuildTypeForm {
    private final SBuildType myBuildType;
    private final VcsSettingsBean myVcsBean;

    public MockBuildTypeForm(SBuildType buildType, VcsSettingsBean bean) {
      super(buildType.getProject());
      myBuildType = buildType;
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
  }

  class MockVcsSettingsBean extends VcsSettingsBean {


    public MockVcsSettingsBean(@NotNull SProject project, @NotNull BuildTypeSettings buildTypeSettings, @NotNull VcsManager vcsManager, @NotNull ProjectManager projectManager) {
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
  }


}
