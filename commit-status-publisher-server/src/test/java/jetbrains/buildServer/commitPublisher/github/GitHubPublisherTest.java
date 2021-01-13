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

package jetbrains.buildServer.commitPublisher.github;

import com.google.gson.Gson;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.HttpPublisherTest;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapperImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.Permissions;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.RepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
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
public class GitHubPublisherTest extends HttpPublisherTest {

  private ChangeStatusUpdater myChangeStatusUpdater;

  public GitHubPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*build started.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*success.*build finished.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*build failed.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*build failed.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*success.*build finished.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*build started.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*build failed.*%s.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, String.format(".*/repos/owner/project .*")); // not to be tested
  }


  public void test_buildFinishedSuccessfully_server_url_with_subdir() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v3");
    params.put(Constants.GITHUB_SERVER, getServerUrl() + "/subdir/api/v3");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems);
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v3");
    params.put(Constants.GITHUB_SERVER, getServerUrl() + "/subdir/api/v3/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems);
    test_buildFinished_Successfully();
  }


  public void should_fail_with_error_on_wrong_vcs_url() {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch (PublisherException ex) {
      then(ex.getMessage()).matches("Cannot parse.*" + myVcsRoot.getName() + ".*");
    }
  }


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("");
    setExpectedEndpointPrefix("/repos/" + OWNER + "/" + CORRECT_REPO);
    super.setUp();

    Map<String, String> params = getPublisherParams();

    myChangeStatusUpdater = new ChangeStatusUpdater(new GitHubApiFactoryImpl(new HttpClientWrapperImpl(new HTTPRequestBuilder.ApacheClient43RequestHandler(), () -> null)),
                                                    myWebLinks, myFixture.getVcsHistory());

    myPublisherSettings = new GitHubSettings(myChangeStatusUpdater, myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems,
                                             myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext(),
                                             myTrustStoreProvider);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems);
  }

  @Override
  protected void setPublisherTimeout(int timeout) {
    super.setPublisherTimeout(timeout);
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {
        @NotNull
        @Override
        public Map<String, String> getUserDefinedProperties() {
          return Collections.singletonMap("teamcity.github.http.timeout", String.valueOf(timeout));
        }
      });
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/repos" +  "/" + OWNER + "/" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
    } else if (url.contains("/repos"  + "/" + OWNER + "/" +  READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    Gson gson = new Gson();
    RepoInfo repoInfo = new RepoInfo();
    repoInfo.name = repoName;
    repoInfo.permissions = new Permissions();
    repoInfo.permissions.pull = true;
    repoInfo.permissions.push = isPushPermitted;
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.GITHUB_USERNAME, "user");
      put(Constants.GITHUB_PASSWORD, "pwd");
      put(Constants.GITHUB_SERVER, getServerUrl());
    }};
  }
}