package jetbrains.buildServer.commitPublisher.stash;

import com.google.gson.Gson;
import java.util.Collections;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.stash.data.StashRepoInfo;
import jetbrains.buildServer.commitPublisher.stash.data.StashServerInfo;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

@Test
public class StashPublisherTest extends BaseStashPublisherTest {

  public StashPublisherTest() {
    myServerVersion = "6.0";
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build started.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Running with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*with a comment by %s.*%s.*", REVISION, PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*Failure.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*api/.*/owner/repos/project.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/rest");
    setExpectedEndpointPrefix("/build-status/1.0/commits");
    super.setUp();
  }
}