package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
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

import static org.apache.http.client.protocol.HttpClientContext.AUTH_CACHE;

/**
 * @author anton.zamolotskikh, 23/11/16.
 */
public class HttpHelper {
  private static final Logger LOG = Logger.getInstance(HttpBasedCommitStatusPublisher.class.getName());

  public static void post(@NotNull String url, @Nullable String username, @Nullable String password,
                          @Nullable String data, @Nullable ContentType contentType,
                          @Nullable final Map<String, String> headers, int timeout,
                          @Nullable HttpResponseProcessor processor) throws IOException, HttpPublisherException {

    URI uri = getURI(url);
    CloseableHttpClient client = buildClient(uri, username, password);

    HttpPost post = null;
    HttpResponse response = null;
    try {
      post = new HttpPost(url);
      post.setConfig(makeRequestConfig(timeout));

      addHeaders(post, headers);

      if (null != data && null != contentType) {
        post.setEntity(new StringEntity(data, contentType));
      }
      response = client.execute(post, makeHttpContext(uri));
      if (null != processor) {
        processor.processResponse(response);
      }
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

  public static void get(@NotNull String url, @Nullable String username, @Nullable String password,
                         @Nullable final Map<String, String> headers, int timeout,
                         @Nullable HttpResponseProcessor processor) throws IOException, HttpPublisherException {

    URI uri = getURI(url);
    CloseableHttpClient client = buildClient(uri, username, password);

    HttpGet get = null;
    HttpResponse response = null;
    try {
      get = new HttpGet(url);
      get.setConfig(makeRequestConfig(timeout));

      addHeaders(get, headers);

      response = client.execute(get, makeHttpContext(uri));
      if (null != processor) {
        processor.processResponse(response);
      }
    } finally {
      HttpClientUtils.closeQuietly(response);
      if (get != null) {
        try {
          get.releaseConnection();
        } catch (Exception e) {
          LOG.warn("Error releasing connection", e);
        }
      }
      HttpClientUtils.closeQuietly(client);
    }
  }

  private static void addHeaders(HttpMessage request, @Nullable final Map<String, String> headers) {
    if (null != headers) {
      for (Map.Entry<String, String> hdr : headers.entrySet()) {
        request.addHeader(hdr.getKey(), hdr.getValue());
      }
    }
  }

  private static URI getURI (String url) {
    try {
      return new URI(url);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException(String.format("Malformed URL '%s'", url), ex);
    }
  }

  private static RequestConfig makeRequestConfig(int timeout) {
    return  RequestConfig.custom()
            .setConnectionRequestTimeout(timeout)
            .setConnectTimeout(timeout)
            .setSocketTimeout(timeout)
            .build();
  }

  private static BasicHttpContext makeHttpContext(URI uri) {
    AuthCache authCache = new BasicAuthCache();
    authCache.put(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), new BasicScheme());
    BasicHttpContext ctx = new BasicHttpContext();
    ctx.setAttribute(AUTH_CACHE, authCache);
    return ctx;
  }

  private static CloseableHttpClient buildClient(URI uri, String username, String password) {
    HttpClientBuilder builder = createHttpClientBuilder();
    if (null != username && null != password) {
      BasicCredentialsProvider credentials = new BasicCredentialsProvider();
      credentials.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(username, password));
      builder.setDefaultCredentialsProvider(credentials);
    }
    return builder.useSystemProperties().build();
  }

  @NotNull
  private static HttpClientBuilder createHttpClientBuilder() {
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

}
