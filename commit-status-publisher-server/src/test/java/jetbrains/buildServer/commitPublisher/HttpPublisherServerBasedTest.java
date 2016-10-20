package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.util.io.StreamUtil;
import org.apache.http.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.testng.annotations.Test;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class HttpPublisherServerBasedTest extends PublisherServerBasedTest {

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

  @Override
  protected void setUp() throws Exception {

    myLastRequest = null;

    final SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(TIMEOUT * 2).build();
    ServerBootstrap bootstrap = ServerBootstrap.bootstrap().setSocketConfig(socketConfig).setServerInfo("TEST/1.1")
            .registerHandler("/*", new HttpRequestHandler() {
              @Override
              public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
                myNumberOfCurrentRequests++;
                try {
                  if (!myServerMutex.tryAcquire(TIMEOUT * 2, TimeUnit.MILLISECONDS)) {
                    myNumberOfCurrentRequests--;
                    return;
                  }
                } catch (InterruptedException ex) {
                  fail("Test HTTP server thread interrupted");
                }
                myLastRequest = httpRequest.getRequestLine().toString();
                if (httpRequest instanceof HttpEntityEnclosingRequest) {
                  HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
                  InputStream is = entity.getContent();
                  myLastRequest += "\tENTITY: " + StreamUtil.readText(is);
                  httpResponse.setStatusCode(201);
                }
                myNumberOfCurrentRequests--;
                myClientMutex.release();
              }
            });

    myHttpServer = bootstrap.create();
    myHttpServer.start();
    myVcsURL = getServerUrl() + "/owner/project";
    super.setUp();
  }
}
