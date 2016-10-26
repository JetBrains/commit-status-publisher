package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherServerBasedTest;
import jetbrains.buildServer.commitPublisher.PublishError;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GitlabPublisherTest extends HttpPublisherServerBasedTest {

  public GitlabPublisherTest() {
    myExpectedRegExps.put(Events.QUEUED, null); // not to be tested
    myExpectedRegExps.put(Events.REMOVED, null); // not to be tested
    myExpectedRegExps.put(Events.STARTED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*running.*Build started.*", REVISION));
    myExpectedRegExps.put(Events.FINISHED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*success.*Success.*", REVISION));
    myExpectedRegExps.put(Events.FAILED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*failed.*Failure.*", REVISION));
    myExpectedRegExps.put(Events.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.INTERRUPTED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*canceled.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(Events.FAILURE_DETECTED, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*failed.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(Events.MARKED_SUCCESSFUL, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*success.*marked as successful.*", REVISION));
    myExpectedRegExps.put(Events.MARKED_RUNNING_SUCCESSFUL, String.format(".*/projects/owner%%2Fproject/statuses/%s.*ENTITY:.*running.*Build marked as successful.*", REVISION));
  }

  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublishError ex) {
      then(ex.getMessage()).matches("Cannot parse.*" + myVcsRoot.getName() + ".*");
    }
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.GITLAB_TOKEN, "TOKEN");
      put(Constants.GITLAB_API_URL, getServerUrl());
    }};
    myPublisher = new GitlabPublisher(myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
  }
}