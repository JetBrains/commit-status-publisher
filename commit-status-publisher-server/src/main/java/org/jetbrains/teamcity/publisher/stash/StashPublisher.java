package org.jetbrains.teamcity.publisher.stash;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.publisher.BaseCommitStatusPublisher;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
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
    vote(buildType, build, revision, StashBuildStatus.INPROGRESS, "Build started");
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build) {
    BuildRevision revision = getBuildRevisionForVote(build);
    if (revision == null)
      return;
    SBuildType buildType = build.getBuildType();
    if (buildType == null)
      return;
    StashBuildStatus status = build.getBuildStatus().isSuccessful() ? StashBuildStatus.SUCCESSFUL : StashBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(buildType, build, revision, status, description);
  }

  private void vote(@NotNull SBuildType buildType,
                    @NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    StringBuilder data = new StringBuilder();
    data.append("{")
            .append("\"state\":").append("\"").append(status).append("\",")
            .append("\"key\":").append("\"").append(buildType.getExternalId()).append("\",")
            .append("\"name\":").append("\"").append(getBuildName(build)).append("\",")
            .append("\"url\":").append("\"").append(myLinks.getViewResultsUrl(build)).append("\",")
            .append("\"description\":").append("\"").append(comment).append("\"")
            .append("}");

    try {
      vote(revision.getRevision(), data.toString());
    } catch (Exception e) {
      String problemId = "stash.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "stash.publisher",
              "Error while publishing a commit status to Stash: " + e.getMessage()));
    }
  }

  private void vote(@NotNull String commit, @NotNull String data) throws URISyntaxException, IOException,
          UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    URI stashURI = new URI(getBaseUrl());

    DefaultHttpClient client = new DefaultHttpClient();
    client.getCredentialsProvider().setCredentials(
            new AuthScope(stashURI.getHost(), stashURI.getPort()),
            new UsernamePasswordCredentials(getUsername(), getPassword()));

    AuthCache authCache = new BasicAuthCache();
    authCache.put(new HttpHost(stashURI.getHost(), stashURI.getPort(), stashURI.getScheme()), new BasicScheme());
    BasicHttpContext ctx = new BasicHttpContext();
    ctx.setAttribute(ClientContext.AUTH_CACHE, authCache);

    String url = getBaseUrl() + "/rest/build-status/1.0/commits/" + commit;

    HttpPost post = new HttpPost(url);
    post.setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));
    HttpResponse response = client.execute(post, ctx);
    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() >= 400)
      throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
  }

  private String getBuildName(@NotNull SBuild build) {
    return build.getFullName() + " #" + build.getBuildNumber();
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
