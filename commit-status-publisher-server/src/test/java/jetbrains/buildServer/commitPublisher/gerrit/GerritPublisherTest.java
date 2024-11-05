

/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.gerrit;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.messages.Status;
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
    myExpectedRegExps.put(EventToTest.STARTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --label Verified=\\+1.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --label Verified=\\-1.*%s", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, null); // not to be tested
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --label Verified=\\-1.*%s.*%s", BT_NAME_ESCAPED_REGEXP, REVISION));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*server: gerrit_server, user: gerrit_user, command: gerrit ls-projects --format JSON.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLastRequest = null;
    myGerritClient = new MockGerritClient();
    myPublisherSettings = new GerritSettings(new MockPluginDescriptor(),
                                             myFixture.getSingletonService(ExtensionHolder.class),
                                             myGerritClient,
                                             myWebLinks, myProblems, myTrustStoreProvider);
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

  public void test_buildFinished_with_no_label() throws Exception {
    Map<String, String> params = getPublisherParams();
    params.remove(Constants.GERRIT_LABEL);
    myPublisher = new GerritPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myGerritClient, myWebLinks, params, myProblems);
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(String.format(".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --label Verified=\\+1.*", REVISION));
  }

  public void test_buildFinished_with_verified_option_in_internal_property() throws Exception {
    Map<String, String> params = getPublisherParams();
    setInternalProperty("teamcity.commitStatusPublisher.gerrit.verified.option", "true");
    myPublisher = new GerritPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myGerritClient, myWebLinks, params, myProblems);
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(String.format(".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --verified \\+1.*", REVISION));
  }

  public void test_buildFinished_with_verified_option_in_settings() throws Exception {
    Map<String, String> params = getPublisherParams();
    params.put(Constants.GERRIT_LABEL, "$verified-option");
    myPublisher = new GerritPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myGerritClient, myWebLinks, params, myProblems);
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(String.format(".*server: gerrit_server, user: gerrit_user, command: gerrit review --project PRJ1 --verified \\+1.*", REVISION));
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
      put(Constants.GERRIT_LABEL, "Verified");
      put(Constants.GERRIT_SUCCESS_VOTE, "+1");
      put(Constants.GERRIT_FAILURE_VOTE, "-1");
    }};
  }

  @Override
  protected String getRequestAsString() {
    return myLastRequest;
  }

  @Override
  protected List<String> getAllRequestsAsString() {
    throw new IllegalStateException("not implemented");
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