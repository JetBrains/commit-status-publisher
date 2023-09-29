/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.stash;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.stash.data.DeprecatedJsonStashBuildStatuses;
import jetbrains.buildServer.commitPublisher.stash.data.JsonStashBuildStatus;
import jetbrains.buildServer.commitPublisher.stash.data.StashRepoInfo;
import jetbrains.buildServer.commitPublisher.stash.data.StashServerInfo;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.testng.annotations.BeforeMethod;

public abstract class BaseStashPublisherTest extends HttpPublisherTest {

  protected String myServerVersion;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = getPublisherParams();
    myPublisherSettings = new StashSettings(new MockPluginDescriptor(),
                                            myWebLinks,
                                            myProblems,
                                            myTrustStoreProvider,
                                            myOAuthConnectionsManager,
                                            myOAuthTokenStorage,
                                            myFixture.getUserModel(),
                                            myFixture.getSecurityContext(),
                                            myFixture.getProjectManager());
    myPublisher = new StashPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.publishQueuedBuildStatus", "true"));
  }

  @Override
  public void test_testConnection_fails_on_readonly() throws InterruptedException {
    // NOTE: Stash Publisher cannot determine if it has just read only access during connection testing
  }

  public void test_buildFinishedSuccessfully_server_url_with_subdir() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/rest");
    params.put(Constants.STASH_BASE_URL, getServerUrl() + "/subdir");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new StashPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/rest");
    params.put(Constants.STASH_BASE_URL, getServerUrl() + "/subdir/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new StashPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myWebLinks, params, myProblems, new CommitStatusesCache<>());
    test_buildFinished_Successfully();
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(Constants.STASH_USERNAME, "user");
      put(Constants.STASH_PASSWORD, "pwd");
      put(Constants.STASH_BASE_URL, getServerUrl());
    }};
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.endsWith("/rest/api/1.0/application-properties")) {
      StashServerInfo info = new StashServerInfo();
      info.version = myServerVersion;
      info.displayName = "Bitbucket Server";
      httpResponse.setEntity(new StringEntity(gson.toJson(info), StandardCharsets.UTF_8));
    } else if (url.contains("/rest/api/1.0/projects/" + OWNER + "/repos/" + CORRECT_REPO + "/commits/")) {
      responseWithSingleCommitStatus(httpResponse);
    } else if (url.contains("/rest/build-status/1.0/commits/")) {
      responseWithCommitStatuses(httpResponse);
    } else if (url.endsWith(OWNER + "/repos/" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
    } else if (url.endsWith(OWNER + "/repos/" + READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
    } else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  private void responseWithSingleCommitStatus(HttpResponse httpResponse) {
    JsonStashBuildStatus status = new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_QUEUED, BuildServerCreator.DEFAULT_TEST_PROJECT_EXT_ID, null, "My Default Test Project / My Default Test Build Type", null, null, StashBuildStatus.INPROGRESS.name(), null, null);
    String json = gson.toJson(Collections.singleton(status));
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  private void responseWithCommitStatuses(HttpResponse httpResponse) {
    DeprecatedJsonStashBuildStatuses statuses = new DeprecatedJsonStashBuildStatuses();
    statuses.isLastPage = true;
    statuses.size = 1;

    DeprecatedJsonStashBuildStatuses.Status status = new DeprecatedJsonStashBuildStatuses.Status();
    status.name = "My Default Test Project / My Default Test Build Type";
    status.key = "MyDefaultTestBuildType";
    status.state = StashBuildStatus.INPROGRESS.name();
    status.description = DefaultStatusMessages.BUILD_QUEUED;
    statuses.values = Collections.singleton(status);
    String json = gson.toJson(statuses);
    httpResponse.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    StashRepoInfo repoInfo = new StashRepoInfo();
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  @Override
  protected boolean isStatusCacheNotImplemented() {
    return false;
  }

  @Override
  protected boolean requiresInitialRequest() {
    return true;
  }
}
