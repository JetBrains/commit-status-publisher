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

package jetbrains.buildServer.commitPublisher;

import com.google.gson.Gson;
import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.version.ServerVersionHolder;
import org.apache.http.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class HttpPublisherTest extends CommitStatusPublisherTest {

  protected final static Gson gson = new Gson();
  protected final static String OWNER = "owner";
  protected final static String CORRECT_REPO = "project";
  protected final static String READ_ONLY_REPO = "readonly";
  private final static long GRACEFUL_SHUTDOWN_TIMEOUT = 300;

  private HttpServer myHttpServer;
  private List<String> myRequests;
  private String myExpectedApiPath = "";
  private String myExpectedEndpointPrefix = "";
  private int myRespondWithRedirectCode;
  protected boolean myDoNotRespond;
  private String myLastAgent;

  private static final int TIMEOUT = 2000;
  private static final int SHORT_TIMEOUT_TO_FAIL = 100;


  @DataProvider(name = "provideRedirectCodes")
  public Object [][] provideRedirectCodes() {
    return new Object [][] {{301}, {302}, {307}};
  }

  public void test_user_agent_is_teamcity() throws Exception {
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FINISHED));
    then(myLastAgent).isEqualTo("TeamCity Server " + ServerVersionHolder.getVersion().getDisplayVersion()
                                + " (build " + ServerVersionHolder.getVersion().getBuildNumber() + ")");
  }

  @Test(dataProvider = "provideRedirectCodes")
  public void test_redirection(int httpCode) throws Exception {
    myRespondWithRedirectCode = httpCode;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  public void should_report_timeout_failure() throws Exception {
    setPublisherTimeout(SHORT_TIMEOUT_TO_FAIL);
    myDoNotRespond = true;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(getRequestAsString()).isNull();
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    then(problems.iterator().next().getProblem().getDescription()).matches(String.format("Commit Status Publisher.*%s.*timed out.*", myPublisher.getId()));
  }

  protected boolean isStatusCacheNotImplemented() {
    return true;
  }

  public void should_cahce_get_requests_when_toggles_on() throws PublisherException {
    if (isStatusCacheNotImplemented()) return;

    setInternalProperty("teamcity.commitStatusPubliser.checkStatus.enabled", "true");
    setInternalProperty("teamcity.commitStatusPublisher.statusCache.enabled", "true");
    SFinishedBuild build = myFixture.createBuild(myBuildType, Status.NORMAL);
    BuildPromotion buildPromotion = build.getBuildPromotion();
    myPublisher.getRevisionStatus(buildPromotion, myRevision);
    int expectedRequests = requiresInitialRequest() ?
                           requiresAuthPreRequest() ? 4 : 2 :
                           requiresAuthPreRequest() ? 2 : 1;
    assertEquals(expectedRequests, countRequests());
    myPublisher.getRevisionStatus(buildPromotion, myRevision);
    assertEquals(expectedRequests, countRequests());   // new request to API was not sent
  }

  public void should_reset_cache_value_on_status_post_when_toggles_on() throws PublisherException {
    if (isStatusCacheNotImplemented()) return;

    setInternalProperty("teamcity.commitStatusPubliser.checkStatus.enabled", "true");
    setInternalProperty("teamcity.commitStatusPublisher.statusCache.enabled", "true");
    SFinishedBuild build = myFixture.createBuild(myBuildType, Status.NORMAL);
    BuildPromotion buildPromotion = build.getBuildPromotion();
    myPublisher.getRevisionStatus(buildPromotion, myRevision);
    int expectedRequests = requiresInitialRequest() ?
                            requiresAuthPreRequest() ? 4 : 2 :
                            requiresAuthPreRequest() ? 2 : 1;
    assertEquals(expectedRequests, countRequests());
    assertTrue(getRequestAsString().contains("GET"));

    myPublisher.buildStarted(build, myRevision);
    expectedRequests += (requiresAuthPreRequest() ? 2 : 1);
    assertEquals(expectedRequests, countRequests());
    assertTrue(getRequestAsString().contains("POST"));

    myPublisher.getRevisionStatus(buildPromotion, myRevision);
    expectedRequests += (requiresAuthPreRequest() ? 2 : 1);
    assertEquals(expectedRequests, countRequests());
    assertTrue(getRequestAsString().contains("GET"));
  }

  protected boolean requiresAuthPreRequest() {
    return false;
  }

  protected boolean requiresInitialRequest() {
    return false;
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {

    myRequests = new ArrayList<>();
    myLastAgent = null;
    myDoNotRespond = false;
    myRespondWithRedirectCode = 0;

    final SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(TIMEOUT * 2).build();
    ServerBootstrap bootstrap = ServerBootstrap.bootstrap().setSocketConfig(socketConfig).setServerInfo("TEST/1.1")
                                               .registerHandler("/*", new HttpRequestHandler() {
                                                 @Override
                                                 public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
                                                   mockRequestHandler(httpRequest, httpResponse, httpContext);
                                                 }
                                               });

    myHttpServer = bootstrap.create();
    myHttpServer.start();
    myVcsURL = getServerUrl() + "/" + OWNER + "/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl()  + "/" + OWNER + "/" + READ_ONLY_REPO;
    super.setUp();
  }

  protected void mockRequestHandler(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
    myLastAgent = httpRequest.getLastHeader("User-Agent").getValue();
    if (myRespondWithRedirectCode > 0) {
      setRedirectionResponse(httpRequest, httpResponse);
      return;
    }

    try {
      if (myDoNotRespond) {
        Thread.sleep(SHORT_TIMEOUT_TO_FAIL * 2);
        return;
      }
    } catch (InterruptedException ex) {
      httpResponse.setStatusCode(500);
      return;
    }
    StringBuilder request = new StringBuilder();
    request.append(httpRequest.getRequestLine().toString());
    String requestData = null;
    if (httpRequest instanceof HttpEntityEnclosingRequest) {
      HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
      InputStream is = entity.getContent();
      requestData = StreamUtil.readText(is);
      request.append("\tENTITY: ").append(requestData);
      httpResponse.setStatusCode(201);
    } else {
      httpResponse.setStatusCode(200);
    }
    myRequests.add(request.toString());
    if(!populateResponse(httpRequest, requestData, httpResponse)) {
      myRequests.add("HTTP error: " + httpResponse.getStatusLine() + " for request " + request);
    }
  }

  protected void setPublisherTimeout(int timeout) {
    myPublisher.setConnectionTimeout(timeout);
  }

  @Override
  protected String getRequestAsString() {
    return myRequests.isEmpty() ? null : myRequests.get(myRequests.size() - 1);
  }

  protected int countRequests() {
    return myRequests.size();
  }

  protected String getServerUrl() {
    return "http://localhost:" + String.valueOf(myHttpServer.getLocalPort());
  }

  protected void setRedirectionResponse(final HttpRequest httpRequest, final HttpResponse httpResponse) {
    httpResponse.setStatusCode(307);
    httpResponse.setHeader("Location", httpRequest.getRequestLine().getUri());
    myRespondWithRedirectCode = 0;
  }

  protected boolean populateResponse(HttpRequest httpRequest, String requestData, HttpResponse httpResponse) {
    RequestLine requestLine = httpRequest.getRequestLine();
    String method = requestLine.getMethod();
    String url = requestLine.getUri();
    if (method.equals("GET")) {
      return respondToGet(url, httpResponse);
    } else if (method.equals("POST")) {
      return respondToPost(url, requestData, httpRequest, httpResponse);
    } else if (method.equals("PATCH")) {
      return respondToPatch(url, requestData, httpRequest, httpResponse);
    }

    respondWithError(httpResponse, 405, String.format("Wrong method '%s'", method));
    return false;
  }

  protected abstract boolean respondToGet(String url, HttpResponse httpResponse);

  protected abstract boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse);

  protected boolean respondToPatch(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return false;
  }

  protected boolean isUrlExpected(String url, HttpResponse httpResponse) {
    String expected = getExpectedApiPath() + getExpectedEndpointPrefix();
    if(!url.startsWith(expected)) {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: '%s' expected: '%s'", url, expected));
      return false;
    }
    return true;
  }

  protected void respondWithError(HttpResponse httpResponse, int statusCode, String msg) {
    httpResponse.setStatusCode(statusCode);
    httpResponse.setReasonPhrase(msg);
  }

  @AfterMethod(alwaysRun = true)
  @Override
  protected void tearDown() throws Exception {
    if (myHttpServer != null) {
      myHttpServer.shutdown(GRACEFUL_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
    }
    super.tearDown();
  }

  protected void setExpectedApiPath(String path) {
    myExpectedApiPath = path;
  }

  protected String getExpectedApiPath() {
    return myExpectedApiPath;
  }

  protected void setExpectedEndpointPrefix(String prefix) {
    myExpectedEndpointPrefix = prefix;
  }

  protected String getExpectedEndpointPrefix() {
    return myExpectedEndpointPrefix;
  }
}
