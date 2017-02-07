package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.util.io.StreamUtil;
import org.apache.http.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class HttpPublisherTest extends AsyncPublisherTest {

  protected String OWNER = "owner";
  protected String CORRECT_REPO = "project";
  protected String READ_ONLY_REPO = "readonly";

  private HttpServer myHttpServer;
  private int myNumberOfCurrentRequests = 0;
  private String myLastRequest;

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

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {

    myLastRequest = null;

    final SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(TIMEOUT * 2).build();
    ServerBootstrap bootstrap = ServerBootstrap.bootstrap().setSocketConfig(socketConfig).setServerInfo("TEST/1.1")
            .registerHandler("/*", new HttpRequestHandler() {
              @Override
              public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
                myNumberOfCurrentRequests++;
                myProcessingStarted.release(); // indicates that we are processing request
                try {
                  if (!myServerMutex.tryAcquire(TIMEOUT * 2, TimeUnit.MILLISECONDS)) {
                    myNumberOfCurrentRequests--;
                    return;
                  }
                } catch (InterruptedException ex) {
                  httpResponse.setStatusCode(500);
                  return;
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
                populateResponse(httpRequest, requestData, httpResponse);
                myNumberOfCurrentRequests--;
                myProcessingFinished.release();
              }
            });

    myHttpServer = bootstrap.create();
    myHttpServer.start();
    myVcsURL = getServerUrl() + "/" + OWNER + "/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl()  + "/" + OWNER + "/" + READ_ONLY_REPO;
    super.setUp();
  }

  protected void populateResponse(HttpRequest httpRequest, String requestData, HttpResponse httpResponse) {

  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myHttpServer.stop();
  }
}
