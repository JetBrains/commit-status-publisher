package jetbrains.buildServer.commitPublisher.github;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherServerBasedTest;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapperImpl;
import jetbrains.buildServer.commitPublisher.stash.StashPublisher;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GitHubPublisherTest extends HttpPublisherServerBasedTest {

  public GitHubPublisherTest() {
    myExpectedRegExps.put(Events.QUEUED, null); // not to be tested
    myExpectedRegExps.put(Events.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(Events.STARTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*build started.*", REVISION));
    myExpectedRegExps.put(Events.FINISHED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*success.*build finished.*", REVISION));
    myExpectedRegExps.put(Events.FAILED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*build failed.*", REVISION));
    myExpectedRegExps.put(Events.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.INTERRUPTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*", REVISION));
    myExpectedRegExps.put(Events.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(Events.MARKED_SUCCESSFUL, null); // not to be tested
    myExpectedRegExps.put(Events.MARKED_RUNNING_SUCCESSFUL, null); // not to be tested
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.GITHUB_USERNAME, "user");
      put(Constants.GITHUB_PASSWORD, "pwd");
      put(Constants.GITHUB_SERVER, getServerUrl());
    }};

    ChangeStatusUpdater changeStatusUpdater = new ChangeStatusUpdater(myExecServices,
            new GitHubApiFactoryImpl(new HttpClientWrapperImpl()), myWebLinks);
    myPublisher = new GitHubPublisher(changeStatusUpdater, params);
  }
}