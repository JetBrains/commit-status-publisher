package org.jetbrains.teamcity.publisher.stash;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.WebLinks;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.publisher.BaseCommitStatusPublisher;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Map;

public class StashPublisher extends BaseCommitStatusPublisher {

  private final WebLinks myLinks;

  public StashPublisher(@NotNull WebLinks links,
                        @NotNull Map<String, String> params) {
    super(params);
    myLinks = links;
  }

  @Override
  public void buildStarted(@NotNull SRunningBuild build) {
    BuildRevision revision = getBuildRevisionForVote(build);
    if (revision == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;
    StringBuilder data = new StringBuilder();
    data.append("{")
            .append("\"state\":").append("\"").append("INPROGRESS").append("\",")
            .append("\"key\":").append("\"").append(buildType.getExternalId()).append("\",")
            .append("\"name\":").append("\"").append(getBuildName(build)).append("\",")
            .append("\"url\":").append("\"").append(myLinks.getViewResultsUrl(build)).append("\",")
            .append("\"description\":").append("\"").append("Build started").append("\"")
            .append("}");

    try {
      vote(revision.getRevision(), data.toString());
    } catch (Exception e) {
      String problemId = "stash.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "stash.publisher", e.getMessage()));
    }
  }

  @Override
  public void buildFinished(@NotNull SRunningBuild build) {
    BuildRevision revision = getBuildRevisionForVote(build);
    if (revision == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;
    String state = build.getBuildStatus().isSuccessful() ? "SUCCESSFUL" : "FAILED";
    String description = build.getBuildStatus().isSuccessful() ? "Successful build" : "Build failed";
    StringBuilder data = new StringBuilder();
    data.append("{")
            .append("\"state\":").append("\"").append(state).append("\",")
            .append("\"key\":").append("\"").append(buildType.getExternalId()).append("\",")
            .append("\"name\":").append("\"").append(getBuildName(build)).append("\",")
            .append("\"url\":").append("\"").append(myLinks.getViewResultsUrl(build)).append("\",")
            .append("\"description\":").append("\"").append(description).append("\"")
            .append("}");

    try {
      vote(revision.getRevision(), data.toString());
    } catch (Exception e) {
      String problemId = "stash.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "stash.publisher", e.getMessage()));
    }
  }

  private String getBuildName(SRunningBuild build) {
    return build.getFullName() + " #" + build.getBuildNumber();
  }

  private void vote(@NotNull String commit,
                    @NotNull String data) throws URISyntaxException, IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    URI stashURI = new URI(getBaseUrl());

    DefaultHttpClient client = new DefaultHttpClient();
    client.getCredentialsProvider().setCredentials(
            new AuthScope(stashURI.getHost(), stashURI.getPort()),
            new UsernamePasswordCredentials(getUsername(), getPassword()));

    TrustStrategy trust = new TrustSelfSignedStrategy();
    X509HostnameVerifier verifier = new X509HostnameVerifier() {
      public void verify(String host, SSLSocket ssl) throws IOException {
      }

      public void verify(String host, X509Certificate cert) throws SSLException {
      }

      public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
      }

      public boolean verify(String s, SSLSession sslSession) {
        return true;
      }
    };

    SSLContext sslCtx = SSLContext.getInstance("SSL");
    sslCtx.init(new KeyManager[]{}, new TrustManager[] {
            new X509TrustManager() {
              public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
              }

              public void checkClientTrusted(X509Certificate[] certs, String authType) {  }

              public void checkServerTrusted(X509Certificate[] certs, String authType) {  }

            }
    }, new java.security.SecureRandom());
    SSLSocketFactory socketFactory = new SSLSocketFactory(sslCtx, verifier);
    Scheme https = new Scheme("https", stashURI.getPort(), socketFactory);

    client.getConnectionManager().getSchemeRegistry().register(https);

    AuthCache authCache = new BasicAuthCache();
    authCache.put(new HttpHost(stashURI.getHost(), stashURI.getPort(), stashURI.getScheme()), new BasicScheme());
    BasicHttpContext ctx = new BasicHttpContext();
    ctx.setAttribute(ClientContext.AUTH_CACHE, authCache);

    HttpPost post = new HttpPost(getBaseUrl() + "/rest/build-status/1.0/commits/" + commit);
    post.setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));
    HttpResponse response = client.execute(post, ctx);
    boolean pause = true;
  }

  String getBaseUrl() {
    return myParams.get("stashBaseUrl");
  }

  private String getUsername() {
    return myParams.get("stashUsername");
  }

  private String getPassword() {
    return myParams.get("secure:stashPassword");
  }
}
