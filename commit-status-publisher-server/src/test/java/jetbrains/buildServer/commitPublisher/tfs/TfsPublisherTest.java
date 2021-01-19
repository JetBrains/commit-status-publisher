/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.04.2017
 *         Time: 18:06
 */
@Test
public class TfsPublisherTest extends HttpPublisherTest {

  TfsPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Pending.*is pending.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Succeeded.*has succeeded.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Failed.*has failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Failed.*has failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Succeeded.*has succeeded.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Pending.*is pending.*", REVISION));
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format("POST /project/_apis/git/repositories/project/commits/%s/statuses.*ENTITY:.*Failed.*%s.*has failed.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, null); // not to be tested
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPublisherSettings = new TfsPublisherSettings(new MockPluginDescriptor(), myWebLinks, myProblems,
                                                   myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext(), myTrustStoreProvider);
    Map<String, String> params = getPublisherParams();
    myPublisher = new TfsStatusPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems);
    myVcsURL = getServerUrl() + "/_git/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl()  + "/_git/" + READ_ONLY_REPO;
    myVcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
  }

  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getMessage()).matches("Invalid URL for TFS Git project.*" + myVcsRoot.getProperty("url") + ".*");
    }
  }

  @Override
  public void test_testConnection() throws Exception {
    try {
      super.test_testConnection();
    } catch (PublisherException e) {
      Assert.assertTrue(e.getMessage().startsWith("TFS publisher has failed to test connection to repository"));
    }
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(TfsConstants.ACCESS_TOKEN, "token");
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains(READ_ONLY_REPO)) {
      httpResponse.setStatusCode(403);
      httpResponse.setEntity(new StringEntity("{'message': 'error'}", "UTF-8"));
      return false;
    }
    return true;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

}
