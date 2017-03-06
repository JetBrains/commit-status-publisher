package jetbrains.buildServer.commitPublisher;

import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.controllers.MockResponse;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.springframework.web.servlet.ModelAndView;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class PublisherSettingsControllerTest extends CommitStatusPublisherTestBase {

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  public void must_test_for_all_relevant_roots() throws Exception {
    HttpServletRequest request = new MockRequest(
      Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID,
      Constants.TEST_CONNECTION_PARAM, Constants.TEST_CONNECTION_YES,
      "projectId", myProject.getExternalId(),
      "id", "buildType:" + myBuildType.getExternalId()
    );
    HttpServletResponse response = new MockResponse();

    SVcsRoot vcs1 = myFixture.addVcsRoot("jetbrains.git", "one");
    vcs1.setName("VCS Root 1");
    SVcsRoot vcs2 = myFixture.addVcsRoot("jetbrains.git", "two");
    vcs2.setName("VCS Root 2");
    SVcsRoot vcs3 = myFixture.addVcsRoot("jetbrains.git", "three");
    vcs3.setName("VCS Root 3");
    SVcsRoot vcs4 = myFixture.addVcsRoot("svn", "four"); // MockPublisherSettings ignore svn roots
    vcs4.setName("VCS Root 4");
    myBuildType.addVcsRoot(vcs1);
    myBuildType.addVcsRoot(vcs2);
    myBuildType.addVcsRoot(vcs3);
    myBuildType.addVcsRoot(vcs4);

    myPublisherSettings.setVcsRootsToFailTestConnection(Arrays.asList("VCS Root 1", "VCS Root 3", "VCS Root 4"));

    mySettingsController.handleRequestInternal(request, response);

    then(((MockResponse)response).getReturnedContent())
      .contains("VCS Root 1")
      .doesNotContain("VCS Root 2")
      .contains("VCS Root 3")
      .doesNotContain("VCS Root 4");
  }
}
