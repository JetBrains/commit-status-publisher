package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BaseJMockTestCase;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.MultipleRunnersBean;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootEntry;
import jetbrains.buildServer.vcs.impl.VcsManagerImpl;
import jetbrains.buildServer.web.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 08/09/16.
 */
@Test
public class CommitStatusPublisherFeatureControllerTest extends BaseJMockTestCase {

  private CommitStatusPublisherFeatureController myController;
  private BuildTypeForm myForm;
  private SBuildType myBuildType;
  private SProject myProject;
  private VcsSettingsBean myBean;
  private VcsManager myVcsManager;
  private ProjectManager myProjectManager;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    WebControllerManager wcm = new MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    myProjectManager = m.mock(ProjectManager.class);
    PublisherManager pubManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>emptyList());
    myController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, pubManager,
            new PublisherSettingsController(wcm, pluginDescr, pubManager));
    myBuildType = m.mock(SBuildType.class);
    myProject = m.mock(SProject.class);
    myVcsManager = m.mock(VcsManager.class);

    m.checking(new Expectations() {{
      oneOf(myBuildType).getCheckoutType();
      oneOf(myBuildType).getCheckoutDirectory();
      oneOf(myBuildType).getVcsRootEntries();
      will(returnValue(Collections.<VcsRootEntry>emptyList()));
    }});

    myBean = new MockVcsSettingsBean(myProject, myBuildType, myVcsManager, myProjectManager);


    m.checking(new Expectations() {{
      oneOf(myBuildType).getProject();
      will(returnValue(myProject));
    }});

    myForm = new MockBuildTypeForm(myBuildType, myBean);
  }


  public void must_find_attached_vcs_root() throws Exception {
    final HttpServletRequest request = m.mock(HttpServletRequest.class);
    final HttpServletResponse response = m.mock(HttpServletResponse.class);

    final long vcsRootInternalId = 42;
    final String vcsRootId = "VcsId1";

    final SVcsRoot vcs = m.mock(SVcsRoot.class);

    final Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.VCS_ROOT_ID_PARAM, vcsRootId);
    }};

    m.checking(new Expectations(){{
      oneOf(vcs).getId();
      will(returnValue(vcsRootInternalId));
    }});

    myBean.addVcsRoot(vcs);

    setHandleRequestExpectations(request, params);
    m.checking(new Expectations() {{
      oneOf(myVcsManager).findRootById(42);
      will(returnValue(vcs));
      oneOf(vcs).getExternalId();
      will(returnValue(vcsRootId));
    }});

    ModelAndView mv = myController.handleRequestInternal(request, response);

    then(mv.getModel().get("hasMissingVcsRoot")).isNull();
  }


  public void must_find_attached_vcs_root_by_internal_id() throws Exception {
    final HttpServletRequest request = m.mock(HttpServletRequest.class);
    final HttpServletResponse response = m.mock(HttpServletResponse.class);

    final long vcsRootInternalId = 42;
    final String vcsRootId = "VcsId1";

    final SVcsRoot vcs = m.mock(SVcsRoot.class);

    final Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.VCS_ROOT_ID_PARAM, String.valueOf(vcsRootInternalId));
    }};

    m.checking(new Expectations(){{
      oneOf(vcs).getId();
      will(returnValue(vcsRootInternalId));
    }});

    myBean.addVcsRoot(vcs);

    setHandleRequestExpectations(request, params);
    m.checking(new Expectations() {{
      oneOf(myVcsManager).findRootById(42);
      will(returnValue(vcs));
      oneOf(vcs).getExternalId();
      will(returnValue(vcsRootId));
      oneOf(vcs).getId();
      will(returnValue(vcsRootInternalId));
      oneOf(vcs).getExternalId();
      will(returnValue(vcsRootId));
    }});

    ModelAndView mv = myController.handleRequestInternal(request, response);

    then(mv.getModel().get("hasMissingVcsRoot")).isNull();
  }


  public void must_be_report_missing_vcs_root() throws Exception {
    final HttpServletRequest request = m.mock(HttpServletRequest.class);
    final HttpServletResponse response = m.mock(HttpServletResponse.class);

    final String missingVcsRootId = "ExtId";
    final Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.VCS_ROOT_ID_PARAM, missingVcsRootId);
    }};

    setHandleRequestExpectations(request, params);
    m.checking(new Expectations() {{
      oneOf(myProjectManager).findVcsRootByExternalId(missingVcsRootId);
      will(returnValue(null));
    }});

    ModelAndView mv = myController.handleRequestInternal(request, response);

    then(mv.getModel().get("hasMissingVcsRoot")).isEqualTo(true);
  }

  private void setHandleRequestExpectations(final HttpServletRequest request, final Map<String, String> params) {
    m.checking(new Expectations() {{
        oneOf(request).getAttribute("propertiesBean");
        will(returnValue(new BasePropertiesBean(params)));
        oneOf(request).getAttribute("buildForm");
        will(returnValue(myForm));
        oneOf(request).getAttribute("buildForm");
        will(returnValue(myForm));
        oneOf(myProject).getExternalId();
        will(returnValue("Project1"));
        oneOf(request).getServletPath();
        oneOf(request).getParameter("jsp");
        will(returnValue("fake.jsp"));
      }});
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

  private class MockBuildTypeForm extends BuildTypeForm {
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

  private class MockVcsSettingsBean extends VcsSettingsBean {


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
