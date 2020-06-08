package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.controllers.MockResponse;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import org.springframework.web.servlet.ModelAndView;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherFeatureControllerTest extends CommitStatusPublisherTestBase {
  private VcsSettingsBean myBean;
  private HttpServletRequest myRequest;
  private HttpServletResponse myResponse;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBean = new MockVcsSettingsBean(myProject, myBuildType, myVcsManager, myProjectManager);
    final BuildTypeForm form = new MockBuildTypeForm(myBuildType, myBean);
    myRequest = new MockRequest();
    myRequest.setAttribute("buildForm", form);
    myResponse = new MockResponse();
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
    List<VcsRoot> vcsRoots = (List<VcsRoot>) mv.getModel().get("vcsRoots");
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
