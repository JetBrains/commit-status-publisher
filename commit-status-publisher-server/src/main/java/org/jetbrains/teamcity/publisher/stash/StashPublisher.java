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
import org.jetbrains.annotations.Nullable;
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
  public void buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.INPROGRESS, "Build queued");
  }

  @Override
  public void buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
    StringBuilder description = new StringBuilder("Build removed from queue");
    if (user != null)
      description.append(" by ").append(user.getName());
    if (comment != null)
      description.append(" with comment \"").append(comment).append("\"");
    vote(build, revision, StashBuildStatus.FAILED, "Build removed from queue");
  }

  @Override
  public void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.INPROGRESS, "Build started");
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    StashBuildStatus status = build.getBuildStatus().isSuccessful() ? StashBuildStatus.SUCCESSFUL : StashBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(build, revision, status, description);
  }

  @Override
  public void buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @NotNull User user, @NotNull String comment) {
    StashBuildStatus status = build.getBuildStatus().isSuccessful() ? StashBuildStatus.SUCCESSFUL : StashBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(build, revision, status, description + " with a comment by " + user.getExtendedName() + ": \"" + comment + "\"");
  }

  @Override
  public void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.FAILED, build.getStatusDescriptor().getText());
  }

  @Override
  public void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.FAILED, build.getStatusDescriptor().getText());
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    String msg = createMessage(status, build.getBuildPromotion().getBuildTypeExternalId(), getBuildName(build), myLinks.getViewResultsUrl(build), comment);
    try {
      vote(revision.getRevision(), msg);
    } catch (Exception e) {
      String problemId = "stash.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "stash.publisher",
              "Error while publishing a commit status to Stash: " + e.getMessage()));
    }
  }

  private void vote(@NotNull SQueuedBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    String msg = createMessage(status, build.getBuildPromotion().getBuildTypeExternalId(), getBuildName(build), myLinks.getQueuedBuildUrl(build), comment);
    try {
      vote(revision.getRevision(), msg);
    } catch (Exception e) {
      String problemId = "stash.publisher." + revision.getRoot().getId();
      ((BuildPromotionEx) build.getBuildPromotion()).addBuildProblem(BuildProblemData.createBuildProblem(problemId, "stash.publisher",
              "Error while publishing a commit status to Stash: " + e.getMessage()));
    }
  }

  @NotNull
  private String createMessage(@NotNull StashBuildStatus status,
                               @NotNull String id,
                               @NotNull String name,
                               @NotNull String url,
                               @NotNull String description) {
    StringBuilder data = new StringBuilder();
    data.append("{")
            .append("\"state\":").append("\"").append(status).append("\",")
            .append("\"key\":").append("\"").append(id).append("\",")
            .append("\"name\":").append("\"").append(name).append("\",")
            .append("\"url\":").append("\"").append(url).append("\",")
            .append("\"description\":").append("\"").append(WebUtil.escapeForJavaScript(description, false, false)).append("\"")
            .append("}");
    return data.toString();
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

  @NotNull
  private String getBuildName(@NotNull SBuild build) {
    return build.getFullName() + " #" + build.getBuildNumber();
  }

  @NotNull
  private String getBuildName(@NotNull SQueuedBuild build) {
    return build.getBuildType().getName();
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
