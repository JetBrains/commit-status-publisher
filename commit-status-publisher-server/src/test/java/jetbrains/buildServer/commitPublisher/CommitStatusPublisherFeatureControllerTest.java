

package jetbrains.buildServer.commitPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.controllers.MockResponse;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.springframework.web.servlet.ModelAndView;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherFeatureControllerTest extends CommitStatusPublisherTestBase {
  protected PublisherSettingsController mySettingsController;
  private VcsSettingsBean myBean;
  private HttpServletRequest myRequest;
  private HttpServletResponse myResponse;

  private CommitStatusPublisherFeatureController myFeatureController;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBean = new MockVcsSettingsBean(myProject, myBuildType, myVcsManager, myProjectManager);
    myRequest = new MockRequest();
    final BuildTypeForm form = new MockBuildTypeForm(myBuildType, myBean);
    myRequest.setAttribute("buildForm", form);
    myResponse = new MockResponse();
    WebControllerManager wcm = new MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    PublisherManager publisherManager = new PublisherManager(myServer);
    mySettingsController = new PublisherSettingsController(wcm, pluginDescr, publisherManager, myProjectManager, myFixture.getSingletonService(SecurityContext.class));
    myFeatureController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, publisherManager, mySettingsController, myFixture.getSecurityContext());
  }


  public void must_find_attached_vcs_root() throws Exception {
    final String vcsRootId = "VcsId1";
    final SVcsRoot vcs = myFixture.addVcsRoot("jetbrains.git", "vcs1");
    vcs.setExternalId(vcsRootId);

    final Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID);
      put(Constants.VCS_ROOT_ID_PARAM, vcsRootId);
    }};

    myRequest.setAttribute("propertiesBean", new BasePropertiesBean(params));

    myBean.addVcsRoot(vcs);

    ModelAndView mv = myFeatureController.handleRequestInternal(myRequest, myResponse);

    then(mv.getModel().get("hasMissingVcsRoot")).isNull();
    List<VcsRoot> vcsRoots = (List<VcsRoot>)mv.getModel().get("vcsRoots");
    then(vcsRoots).isNotNull();
    then(vcsRoots).contains(vcs);
  }


  public void must_find_attached_vcs_root_by_internal_id() throws Exception {
    final SVcsRoot vcs = myFixture.addVcsRoot("vcs", "vcs1");
    final long vcsRootInternalId = vcs.getId();

    final Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID);
      put(Constants.VCS_ROOT_ID_PARAM, String.valueOf(vcsRootInternalId));
    }};

    myBean.addVcsRoot(vcs);

    myRequest.setAttribute("propertiesBean", new BasePropertiesBean(params));

    ModelAndView mv = myFeatureController.handleRequestInternal(myRequest, myResponse);

    then(mv.getModel().get("hasMissingVcsRoot")).isNull();
  }


  public void must_be_report_missing_vcs_root() throws Exception {
    final String missingVcsRootId = "ExtId";
    final Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID);
      put(Constants.VCS_ROOT_ID_PARAM, missingVcsRootId);
    }};

    myRequest.setAttribute("propertiesBean", new BasePropertiesBean(params));

    ModelAndView mv = myFeatureController.handleRequestInternal(myRequest, myResponse);

    then(mv.getModel().get("hasMissingVcsRoot")).isEqualTo(true);
  }
}
