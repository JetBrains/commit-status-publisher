package jetbrains.buildServer.commitPublisher.stash;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

public abstract class BaseStashPublisherTest extends HttpPublisherTest {

  protected String myServerVersion;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Map<String, String> params = getPublisherParams();
    myPublisherSettings = new StashSettings(myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems, myTrustStoreProvider);
    myPublisher = new StashPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
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
    myPublisher = new StashPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/rest");
    params.put(Constants.STASH_BASE_URL, getServerUrl() + "/subdir/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new StashPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myExecServices, myWebLinks, params, myProblems);
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
      httpResponse.setEntity(new StringEntity(new Gson().toJson(info), "UTF-8"));
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

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    Gson gson = new Gson();
    StashRepoInfo repoInfo = new StashRepoInfo();
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }
}
