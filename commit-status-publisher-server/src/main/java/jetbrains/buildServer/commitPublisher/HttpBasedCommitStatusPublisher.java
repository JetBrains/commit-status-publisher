package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.ExceptionUtil;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;

import static org.apache.http.client.protocol.HttpClientContext.AUTH_CACHE;

public abstract class HttpBasedCommitStatusPublisher extends BaseCommitStatusPublisher {

  private static final Logger LOG = Logger.getInstance(HttpBasedCommitStatusPublisher.class.getName());

  private final ExecutorServices myExecutorServices;

  public HttpBasedCommitStatusPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId,
                                        @NotNull final ExecutorServices executorServices,
                                        @NotNull Map<String, String> params,
                                        @NotNull CommitStatusPublisherProblems problems) {
    super(buildType, buildFeatureId, params, problems);
    myExecutorServices = executorServices;
  }

  protected Future postAsync(final String url, final String username, final String password,
                             final String data, final ContentType contentType, final Map<String, String> headers,
                             final String buildDescription) {
    ExecutorService service = myExecutorServices.getLowPriorityExecutorService();
    return service.submit(ExceptionUtil.catchAll("posting commit status", new Runnable() {
      @Override
      public void run() {
        Lock lock = getLocks().get(myBuildType.getExternalId());
        try {
          lock.lock();
          post(url, username, password, data, contentType, headers, getConnectionTimeout());
        } catch (Exception ex) {
          myProblems.reportProblem("Commit Status Publisher HTTP request has failed",
                                   HttpBasedCommitStatusPublisher.this, buildDescription,
                                   url, ex, LOG);
        } finally {
          lock.unlock();
        }
      }
    }));
  }

  private void post(@NotNull String url, @Nullable String username, @Nullable String password,
                    @NotNull String data, @NotNull ContentType contentType,
                    @Nullable final Map<String, String> headers, int timeout) throws IOException, HttpPublisherException {
    URI uri;
    try {
      uri = new URI(url);
      if (null == uri.getHost()) {
        throw new URISyntaxException(url, "Host name missing");
      }
    } catch (URISyntaxException ex) {
      throw new PublishError(String.format("Malformed URL '%s'", url), ex);
    }

    HttpClientBuilder builder = createHttpClientBuilder();
    if (null != username && null != password) {
      BasicCredentialsProvider credentials = new BasicCredentialsProvider();
      credentials.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(username, password));
      builder.setDefaultCredentialsProvider(credentials);
    }

    CloseableHttpClient client = builder.build();

    HttpPost post = null;
    HttpResponse response = null;
    try {
      AuthCache authCache = new BasicAuthCache();
      authCache.put(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), new BasicScheme());
      BasicHttpContext ctx = new BasicHttpContext();
      ctx.setAttribute(AUTH_CACHE, authCache);

      RequestConfig requestConfig = RequestConfig.custom()
              .setConnectionRequestTimeout(timeout)
              .setConnectTimeout(timeout)
              .setSocketTimeout(timeout)
              .build();

      post = new HttpPost(url);
      post.setConfig(requestConfig);

      if (null != headers) {
        for (Map.Entry<String, String> hdr : headers.entrySet()) {
          post.addHeader(hdr.getKey(), hdr.getValue());
        }
      }

      post.setEntity(new StringEntity(data, contentType));
      response = client.execute(post, ctx);
      processResponse(response);
    } finally {
      HttpClientUtils.closeQuietly(response);
      if (post != null) {
        try {
          post.releaseConnection();
        } catch (Exception e) {
          LOG.warn("Error releasing connection", e);
        }
      }
      HttpClientUtils.closeQuietly(client);
    }
  }

  protected void processResponse(HttpResponse response) throws HttpPublisherException {
    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() >= 400)
      throw new RuntimeException(statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
  }

  @NotNull
  private HttpClientBuilder createHttpClientBuilder() {
    SSLContext sslcontext = SSLContexts.createSystemDefault();
    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext) {
      @Override
      public Socket connectSocket(
              int connectTimeout,
              Socket socket,
              HttpHost host,
              InetSocketAddress remoteAddress,
              InetSocketAddress localAddress,
              HttpContext context) throws IOException {
        if (socket instanceof SSLSocket) {
          try {
            PropertyUtils.setProperty(socket, "host", host.getHostName());
          } catch (NoSuchMethodException ex) {       // ignore all that stuff
          } catch (IllegalAccessException ex) {     //
          } catch (InvocationTargetException ex) { //
          }
        }
        return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
      }
    };

    return HttpClients.custom().setSSLSocketFactory(sslsf);
  }


  protected static class HttpPublisherException extends Exception {
    private final int myStatusCode;
    private final String myReason;
    private final String myMessage;

    public HttpPublisherException(int statusCode, String reason, @Nullable String message) {
      myStatusCode = statusCode;
      myReason = reason;
      myMessage = message;
    }

    public int getStatusCode() {
      return myStatusCode;
    }

    public String getReason() {
      return myReason;
    }

    @Nullable
    public String getStashMessage() {
      return myMessage;
    }
  }


}
