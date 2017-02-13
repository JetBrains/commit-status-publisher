package jetbrains.buildServer.commitPublisher.gerrit;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GerritPublisherTest extends CommitStatusPublisherTest {

  private MockGerritClient myGerritClient;
  private String myLastRequest;

  public GerritPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, null);
    myExpectedRegExps.put(EventToTest.FINISHED, ".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --verified \\+1.*");
    myExpectedRegExps.put(EventToTest.FAILED, ".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --verified \\-1.*");
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, null);
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null);
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, null);
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, null);
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*server: gerrit_server, user: gerrit_user, command: gerrit ls-projects --format JSON.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLastRequest = null;
    myGerritClient = new MockGerritClient();
    myPublisherSettings = new GerritSettings(myExecServices, new MockPluginDescriptor(),
                                             myFixture.getSingletonService(ExtensionHolder.class),
                                             myGerritClient,
                                             myWebLinks, myProblems);
    Map<String, String> params = getPublisherParams();
    myPublisher = new GerritPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myGerritClient, myWebLinks, params, myProblems);
    myBranch = "custom_branch";
  }

  @Override
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    // not relevant to Gerrit
  }

  @Override
  public void test_testConnection_fails_on_bad_repo_url() throws InterruptedException {
    // not relevant to Gerrit
  }

  @Override
  public void test_testConnection_fails_on_missing_target() throws InterruptedException {
    test_testConnection_failure("http://localhost/nouser/norepo", getPublisherParams("PRJ_MISSING"));
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return getPublisherParams("PRJ1");
  }


  protected Map<String, String> getPublisherParams(final String gerritProjectName) {
    return new HashMap<String, String>() {{
      put(Constants.GERRIT_PROJECT, gerritProjectName);
      put(Constants.GERRIT_SERVER, "gerrit_server");
      put(Constants.GERRIT_USERNAME, "gerrit_user");
      put(Constants.GERRIT_SUCCESS_VOTE, "+1");
      put(Constants.GERRIT_FAILURE_VOTE, "-1");
    }};
  }

  @Override
  protected int getNumberOfCurrentRequests() {
    return 0;
  }

  @Override
  protected String getRequestAsString() {
    return myLastRequest;
  }

  private class MockGerritClient extends GerritClientBase implements GerritClient {
    @Override
    public String runCommand(@NotNull GerritConnectionDetails connection,
                           @NotNull final String command) throws IOException {
      myLastRequest = String.format("project: %s, server: %s, user: %s, command: %s",
                                    connection.getProject().getName(), connection.getServer(), connection.getUserName(), command);
      return "{\"PRJ1\":{\"id\":\"123\"}}"; 
    }
  }
}