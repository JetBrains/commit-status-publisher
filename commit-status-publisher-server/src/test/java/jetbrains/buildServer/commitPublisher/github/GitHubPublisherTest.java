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
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*success.*build finished.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*pending.*build started.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, String.format(".*/repos/owner/project .*")); // not to be tested
  }


  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    } catch(PublisherException ex) {
      then(ex.getMessage()).matches("Cannot parse.*" + myVcsRoot.getName() + ".*");
    }
  }


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {
        @NotNull
        @Override
        public Map<String, String> getUserDefinedProperties() {
          return Collections.singletonMap("teamcity.github.http.timeout", String.valueOf(TIMEOUT / 2));
        }
      });
    }};


    Map<String, String> params = getPublisherParams();

    ChangeStatusUpdater changeStatusUpdater = new ChangeStatusUpdater(myExecServices,
            new GitHubApiFactoryImpl(new HttpClientWrapperImpl()), myWebLinks);

    myPublisherSettings = new GitHubSettings(changeStatusUpdater, myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems,
                                             myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext());
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, changeStatusUpdater, params, myProblems);
  }

  @Override
  protected void populateResponse(HttpRequest httpRequest, String requestData, HttpResponse httpResponse) {
    super.populateResponse(httpRequest, requestData, httpResponse);
    if (httpRequest.getRequestLine().getMethod().equals("GET")) {
      if (httpRequest.getRequestLine().getUri().contains("/repos" +  "/" + OWNER + "/" + CORRECT_REPO)) {
        respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
      } else if (httpRequest.getRequestLine().getUri().contains("/repos"  + "/" + OWNER + "/" +  READ_ONLY_REPO)) {
        respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
      }
    }
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