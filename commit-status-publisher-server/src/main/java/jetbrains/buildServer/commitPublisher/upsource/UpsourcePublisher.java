package jetbrains.buildServer.commitPublisher.upsource;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.httpclient.URI;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.conn.ssl.SSLSocketFactory;
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
import java.util.HashMap;
import java.util.Map;

public class UpsourcePublisher extends BaseCommitStatusPublisher {

  private static final Logger LOG = Logger.getInstance(UpsourcePublisher.class.getName());

  private static final String UPSOURCE_ENDPOINT = "~buildStatus";
  private static final String PROJECT_FIELD = "project";
  private static final String KEY_FIELD = "key";
  private static final String STATE_FIELD = "state";
  private static final String BUILD_URL_FIELD = "url";
  private static final String BUILD_NAME_FIELD = "name";
  private static final String DESCRIPTION_FIELD = "description";
  private static final String REVISION_FIELD = "revision";
  private static final String REVISION_MESSAGE_FIELD = "revisionMessage";
  private static final String REVISION_DATE_FIELD = "revisionDate";

  private final VcsModificationHistory myVcsHistory;
  private final WebLinks myLinks;
  private final Gson myGson = new Gson();

  public UpsourcePublisher(@NotNull VcsModificationHistory vcsHistory,
                           @NotNull WebLinks links,
                           @NotNull Map<String, String> params) {
    super(params);
    myVcsHistory = vcsHistory;
    myLinks = links;
  }

  @NotNull
  @Override
  public String toString() {
    return "upsource";
  }

  @Override
  public void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, UpsourceStatus.IN_PROGRESS, "Build started");
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    UpsourceStatus status = build.getBuildStatus().isSuccessful() ? UpsourceStatus.SUCCESS : UpsourceStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    publish(build, revision, status, description);
  }

  @Override
  public void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, UpsourceStatus.FAILED, build.getStatusDescriptor().getText());
  }

  @Override
  public void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, UpsourceStatus.FAILED, build.getStatusDescriptor().getText());
  }

  @Override
  public void buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision) {
    publish(build, revision, UpsourceStatus.SUCCESS, build.getStatusDescriptor().getText());
  }

  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull UpsourceStatus status,
                       @NotNull String description) {
    String url = myLinks.getViewResultsUrl(build);
    String commitMessage = null;
    Long commitDate = null;
    if (revision instanceof BuildRevisionEx) {
      Long modId = ((BuildRevisionEx) revision).getModificationId();
      if (modId != null) {
        SVcsModification m = myVcsHistory.findChangeById(modId);
        if (m != null) {
          commitMessage = m.getDescription();
          commitDate = m.getVcsDate().getTime();
        }
      }
    }
    String buildName = build.getFullName() + " #" + build.getBuildNumber();
    String payload = createPayload(myParams.get(Constants.UPSOURCE_PROJECT_ID),
            build.getBuildTypeExternalId(),
            status,
            buildName,
            url,
            description,
            revision.getRevision(),
            commitMessage,
            commitDate);
    try {
      publish(payload);
    } catch (Exception e) {
      reportProblem(build, e);
    }
  }


  private void publish(@NotNull String payload) throws IOException {
    String url = myParams.get(Constants.UPSOURCE_SERVER_URL);
    URI upsourceURI = new URI(url);

    BasicCredentialsProvider credentials = new BasicCredentialsProvider();
    credentials.setCredentials(new AuthScope(upsourceURI.getHost(), upsourceURI.getPort()),
            new UsernamePasswordCredentials(myParams.get(Constants.UPSOURCE_USERNAME),
                    myParams.get(Constants.UPSOURCE_PASSWORD)));

    CloseableHttpClient client = createHttpClientBuilder().setDefaultCredentialsProvider(credentials).build();

    HttpPost post = null;
    HttpResponse response = null;
    try {
      AuthCache authCache = new BasicAuthCache();
      authCache.put(new HttpHost(upsourceURI.getHost(), upsourceURI.getPort(), upsourceURI.getScheme()), new BasicScheme());
      BasicHttpContext ctx = new BasicHttpContext();
      ctx.setAttribute(ClientContext.AUTH_CACHE, authCache);

      String endpointUrl = url + "/" + UPSOURCE_ENDPOINT;
      post = new HttpPost(endpointUrl);
      post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
      response = client.execute(post, ctx);
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() >= 400)
        throw new RuntimeException(statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
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


  @NotNull
  private HttpClientBuilder createHttpClientBuilder() {
    SSLContext sslcontext = SSLContexts.createSystemDefault();
    SSLSocketFactory sslsf = new SSLSocketFactory(sslcontext) {
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
          } catch (NoSuchMethodException ex) {
          } catch (IllegalAccessException ex) {
          } catch (InvocationTargetException ex) {
          }
        }
        return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
      }
    };

    return HttpClients.custom().setSSLSocketFactory(sslsf);
  }


  @NotNull
  private String createPayload(@NotNull String project,
                               @NotNull String statusKey,
                               @NotNull UpsourceStatus status,
                               @NotNull String buildName,
                               @NotNull String buildUrl,
                               @NotNull String description,
                               @NotNull String commitRevision,
                               @Nullable String commitMessage,
                               @Nullable Long commitDate) {
    Map<String, String> data = new HashMap<String, String>();
    data.put(PROJECT_FIELD, project);
    data.put(KEY_FIELD, statusKey);
    data.put(STATE_FIELD, status.getName());
    data.put(BUILD_NAME_FIELD, buildName);
    data.put(BUILD_URL_FIELD, buildUrl);
    data.put(DESCRIPTION_FIELD, description);
    data.put(REVISION_FIELD, commitRevision);
    if (commitMessage != null)
      data.put(REVISION_MESSAGE_FIELD, commitMessage);
    if (commitDate != null)
      data.put(REVISION_DATE_FIELD, commitDate.toString());
    return myGson.toJson(data);
  }


  private void reportProblem(@NotNull SBuild build, @NotNull Exception e) {
    String msg = "Error while publishing status to upsource, build " + LogUtil.describe(build) +
            ", " + e.toString();
    LOG.warn(msg);
    LOG.debug(msg, e);
  }
}
