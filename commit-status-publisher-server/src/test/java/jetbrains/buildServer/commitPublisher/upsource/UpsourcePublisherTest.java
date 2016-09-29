package jetbrains.buildServer.commitPublisher.upsource;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherServerBasedTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class UpsourcePublisherTest extends HttpPublisherServerBasedTest {

  public UpsourcePublisherTest() {
    myExpectedRegExps.put(Events.QUEUED, null); // not to be tested
    myExpectedRegExps.put(Events.REMOVED, null); // not to be tested
    myExpectedRegExps.put(Events.STARTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build started.*in_progress.*%s.*", REVISION));
    myExpectedRegExps.put(Events.FINISHED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Success.*success.*%s.*", REVISION));
    myExpectedRegExps.put(Events.FAILED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Failure.*failed.*%s.*", REVISION));
    myExpectedRegExps.put(Events.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(Events.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(Events.INTERRUPTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*%s.*failed.*%s.*", PROBLEM_DESCR, REVISION));
    myExpectedRegExps.put(Events.FAILURE_DETECTED, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*%s.*failed.*%s.*", PROBLEM_DESCR, REVISION));
    myExpectedRegExps.put(Events.MARKED_SUCCESSFUL, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build marked as successful.*success.*%s.*", REVISION));
    myExpectedRegExps.put(Events.MARKED_RUNNING_SUCCESSFUL, String.format(".*~buildStatus.*ENTITY:.*PRJ1.*Build marked as successful.*in_progress.*%s.*", REVISION));
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = new HashMap<String, String>() {{
      put(Constants.UPSOURCE_PROJECT_ID, "PRJ1");
      put(Constants.UPSOURCE_USERNAME, "user");
      put(Constants.UPSOURCE_PASSWORD, "pwd");
      put(Constants.UPSOURCE_SERVER_URL, getServerUrl());
    }};
    myPublisher = new UpsourcePublisher(myFixture.getVcsHistory(), myExecServices, myWebLinks, params);
  }
}