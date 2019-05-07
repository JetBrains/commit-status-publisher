package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.messages.Status;
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

  protected final static String OWNER = "owner";
  protected final static String CORRECT_REPO = "project";
  protected final static String READ_ONLY_REPO = "readonly";
  private final static long GRACEFUL_SHUTDOWN_TIMEOUT = 300;

  private HttpServer myHttpServer;
  private volatile int myNumberOfCurrentRequests = 0;
  private volatile String myLastRequest;
  private volatile String myExpectedApiPath = "";
  private volatile String myExpectedEndpointPrefix = "";
  private volatile int myRespondWithRedirectCode = 0;
  private volatile String myLastAgent;
  private static final int TIMEOUT = 2000;
  private static final int SHORT_TIMEOUT_TO_FAIL = 100;

  private Semaphore
    myServerMutex, // released if the test wants the server to finish processing a request
    myProcessingFinished, // released by the server to indicate to the test client that it can check the request data
    myProcessingStarted; // released by the server when it has started processing a request


  @DataProvider(name = "provideRedirectCodes")
  public Object [][] provideRedirectCodes() {
    return new Object [][] {{301}, {302}, {307}};
  }

  public void test_user_agent_is_teamcity() throws Exception {
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FINISHED));
    then(myLastAgent).isEqualTo("TeamCity Server " + ServerVersionHolder.getVersion().getDisplayVersion()
                                + " (build " + ServerVersionHolder.getVersion().getBuildNumber() + ")");
  }

  @Test(dataProvider = "provideRedirectCodes")
  public void test_redirection(int httpCode) throws Exception {
    myRespondWithRedirectCode = httpCode;
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
                          .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  public void should_report_publishing_failure() throws Exception {
    setPublisherTimeout(SHORT_TIMEOUT_TO_FAIL);
    myServerMutex = new Semaphore(1);
    myServerMutex.acquire();
    // The HTTP client is supposed to wait for server for twice as less as we are waiting for its results
    // and the test HTTP server is supposed to wait for twice as much
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(myProcessingStarted.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue(); // At least one request must arrive
    // The server mutex is never released, so the server does not respond until the connection times out
    then(waitForRequest(SHORT_TIMEOUT_TO_FAIL * 2)).isNull();
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    then(problems.iterator().next().getProblem().getDescription()).matches(String.format("Commit Status Publisher.*%s.*timed out.*", myPublisher.getId()));
    myServerMutex.release();
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {

    myLastRequest = null;
    myLastAgent = null;
    myServerMutex = null;
    myProcessingFinished = new Semaphore(0);
    myProcessingStarted = new Semaphore(0);

    final SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(TIMEOUT * 2).build();
    ServerBootstrap bootstrap = ServerBootstrap.bootstrap().setSocketConfig(socketConfig).setServerInfo("TEST/1.1")
                                               .registerHandler("/*", new HttpRequestHandler() {
                                                 @Override
                                                 public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
                                                   boolean isPublishingRequest = isPublishingRequest(httpRequest);
                                                   if (isPublishingRequest) {
                                                     myLastAgent = httpRequest.getLastHeader("User-Agent").getValue();
                                                     if (myRespondWithRedirectCode > 0) {
                                                       setRedirectionResponse(httpRequest, httpResponse);
                                                       return;
                                                     }

                                                     myNumberOfCurrentRequests++;
                                                     myProcessingStarted.release(); // indicates that we are processing request
                                                     try {
                                                       if (null != myServerMutex && !myServerMutex.tryAcquire(TIMEOUT * 2, TimeUnit.MILLISECONDS)) {
                                                         myNumberOfCurrentRequests--;
                                                         return;
                                                       }
                                                     } catch (InterruptedException ex) {
                                                       httpResponse.setStatusCode(500);
                                                       myNumberOfCurrentRequests--;
                                                       return;
                                                     }
                                                   }
                                                   myLastRequest = httpRequest.getRequestLine().toString();
                                                   String requestData = null;
                                                   if (httpRequest instanceof HttpEntityEnclosingRequest) {
                                                     HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
                                                     InputStream is = entity.getContent();
                                                     requestData = StreamUtil.readText(is);
                                                     myLastRequest += "\tENTITY: " + requestData;
                                                     httpResponse.setStatusCode(201);
                                                   } else {
                                                     httpResponse.setStatusCode(200);
                                                   }
                                                   if(!populateResponse(httpRequest, requestData, httpResponse)) {
                                                     myLastRequest = "HTTP error: " + httpResponse.getStatusLine();
                                                   }
                                                   if (isPublishingRequest) {
                                                     myNumberOfCurrentRequests--;
                                                     myProcessingFinished.release();
                                                   }
                                                 }
                                               });

    myHttpServer = bootstrap.create();
    myHttpServer.start();
    myVcsURL = getServerUrl() + "/" + OWNER + "/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl()  + "/" + OWNER + "/" + READ_ONLY_REPO;
    super.setUp();
  }

  protected void setPublisherTimeout(int timeout) {
    myPublisher.setConnectionTimeout(timeout);
  }

  @Override
  protected String waitForRequest() throws InterruptedException {
    return waitForRequest(TIMEOUT);
  }

  protected String waitForRequest(long timeout) throws InterruptedException {
    if (!myProcessingFinished.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
      return null;
    }
    return super.waitForRequest();
  }

  @Override
  protected String getRequestAsString() {
    return myLastRequest;
  }

  protected String getServerUrl() {
    return "http://localhost:" + String.valueOf(myHttpServer.getLocalPort());
  }

  @Override
  protected int getNumberOfCurrentRequests() {
    return myNumberOfCurrentRequests;
  }

  protected boolean isPublishingRequest(HttpRequest httpRequest) {
    return true;
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
    }

    respondWithError(httpResponse, 405, String.format("Wrong method '%s'", method));
    return false;
  }

  protected abstract boolean respondToGet(String url, HttpResponse httpResponse);

  protected abstract boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse);

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

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myHttpServer.shutdown(GRACEFUL_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
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
