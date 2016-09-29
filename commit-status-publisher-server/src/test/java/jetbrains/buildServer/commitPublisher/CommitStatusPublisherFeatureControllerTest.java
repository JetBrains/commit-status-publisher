package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jmock.Expectations;
import org.springframework.web.servlet.ModelAndView;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 08/09/16.
 */
@Test
public class CommitStatusPublisherFeatureControllerTest extends CommitStatusPublisherJMockTestBase {
  private BuildTypeForm myForm;
  private VcsSettingsBean myBean;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

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
}
