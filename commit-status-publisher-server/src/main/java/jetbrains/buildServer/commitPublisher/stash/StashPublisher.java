package jetbrains.buildServer.commitPublisher.stash;

import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.PublishError;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Map;

public class StashPublisher extends BaseCommitStatusPublisher {
  private static final String PUBLISH_QUEUED_BUILD_STATUS = "teamcity.stashCommitStatusPublisher.publishQueuedBuildStatus";

  private static final Logger LOG = Logger.getInstance(StashPublisher.class.getName());

  private final WebLinks myLinks;

  public StashPublisher(@NotNull WebLinks links,
                        @NotNull Map<String, String> params) {
    super(params);
    myLinks = links;
  }

  @NotNull
  public String toString() {
    return "stash";
  }

  @Override
  public String getId() {
    return Constants.STASH_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) {
    if (TeamCityProperties.getBoolean(PUBLISH_QUEUED_BUILD_STATUS)) {
      vote(build, revision, StashBuildStatus.INPROGRESS, "Build queued");
      return true;
    }
    return false;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
    if (TeamCityProperties.getBoolean(PUBLISH_QUEUED_BUILD_STATUS)) {
      StringBuilder description = new StringBuilder("Build removed from queue");
      if (user != null)
        description.append(" by ").append(user.getName());
      if (comment != null)
        description.append(" with comment \"").append(comment).append("\"");
      vote(build, revision, StashBuildStatus.FAILED, description.toString());
      return true;
    }
    return false;
  }

  @Override
  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.INPROGRESS, "Build started");
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    StashBuildStatus status = build.getBuildStatus().isSuccessful() ? StashBuildStatus.SUCCESSFUL : StashBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(build, revision, status, description);
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) {
    StashBuildStatus status;
    if (buildInProgress) {
      status = build.getBuildStatus().isSuccessful() ? StashBuildStatus.INPROGRESS : StashBuildStatus.FAILED;
    } else {
      status = build.getBuildStatus().isSuccessful() ? StashBuildStatus.SUCCESSFUL : StashBuildStatus.FAILED;
    }
    String description = build.getStatusDescriptor().getText();
    if (user != null && comment != null) {
      description += " with a comment by " + user.getExtendedName() + ": \"" + comment + "\"";
    }
    vote(build, revision, status, description);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.SUCCESSFUL, "Build marked as successful");
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    String msg = createMessage(status, build.getBuildPromotion().getBuildTypeExternalId(), getBuildName(build), myLinks.getViewResultsUrl(build), comment);
    try {
      vote(revision.getRevision(), msg);
    } catch (Exception e) {
      throw new PublishError("Cannot publish status to Upsource for VCS root " +
              revision.getRoot().getName() + ": " + getMessage(e), e);
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
      throw new PublishError("Cannot publish status to Upsource for VCS root " +
              revision.getRoot().getName() + ": " + getMessage(e), e);
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
            .append("\"description\":").append("\"").append(escape(description)).append("\"")
            .append("}");
    return data.toString();
  }

  @NotNull
  private String escape(@NotNull String str) {
    String result = WebUtil.escapeForJavaScript(str, false, false);
    return result.replaceAll("\\\\'", "'");
  }

  private void vote(@NotNull String commit, @NotNull String data) throws URISyntaxException, IOException,
          UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, StashException {
    URI stashURI = new URI(getBaseUrl());

    DefaultHttpClient client = new DefaultHttpClient();
    HttpPost post = null;
    HttpResponse response = null;
    try {
      client.getCredentialsProvider().setCredentials(
              new AuthScope(stashURI.getHost(), stashURI.getPort()),
              new UsernamePasswordCredentials(getUsername(), getPassword()));

      AuthCache authCache = new BasicAuthCache();
      authCache.put(new HttpHost(stashURI.getHost(), stashURI.getPort(), stashURI.getScheme()), new BasicScheme());
      BasicHttpContext ctx = new BasicHttpContext();
      ctx.setAttribute(ClientContext.AUTH_CACHE, authCache);

      String url = getBaseUrl() + "/rest/build-status/1.0/commits/" + commit;
      post = new HttpPost(url);
      post.setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));
      response = client.execute(post, ctx);
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() >= 400)
        throw new StashException(statusLine.getStatusCode(), statusLine.getReasonPhrase(), parseErrorMessage(response));
    } finally {
      HttpClientUtils.closeQuietly(response);
      releaseConnection(post);
      HttpClientUtils.closeQuietly(client);
    }
  }

  @Nullable
  private String parseErrorMessage(@NotNull HttpResponse response) {
    HttpEntity entity = response.getEntity();
    if (entity == null)
      return null;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      entity.writeTo(out);
      String str = out.toString("UTF-8");
      LOG.debug("Stash response: " + str);
      JsonElement json = new JsonParser().parse(str);
      if (!json.isJsonObject())
        return null;
      JsonObject jsonObj = json.getAsJsonObject();
      JsonElement errors = jsonObj.get("errors");
      if (errors == null || !errors.isJsonArray())
        return null;
      JsonArray errorsArray = errors.getAsJsonArray();
      if (errorsArray.size() == 0)
        return null;
      JsonElement error = errorsArray.get(0);
      if (error == null || !error.isJsonObject())
        return null;
      JsonElement msg = error.getAsJsonObject().get("message");
      return msg != null ? msg.getAsString() : null;
    } catch (IOException e) {
      return null;
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  private void releaseConnection(@Nullable HttpPost post) {
    if (post != null) {
      try {
        post.releaseConnection();
      } catch (Exception e) {
        LOG.warn("Error releasing connection", e);
      }
    }
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
    return myParams.get(Constants.STASH_BASE_URL);
  }

  private String getUsername() {
    return myParams.get(Constants.STASH_USERNAME);
  }

  private String getPassword() {
    return myParams.get(Constants.STASH_PASSWORD);
  }


  private static class StashException extends Exception {
    private final int myStatusCode;
    private final String myReason;
    private final String myMessage;
    public StashException(int statusCode, String reason, @Nullable String message) {
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

  @NotNull
  private String getMessage(@NotNull Exception e) {
    if (e instanceof StashException) {
      StashException se = (StashException) e;
      String result = "response code: " + se.getStatusCode() + ", reason: " + se.getReason();
      String msg = se.getStashMessage();
      if (msg != null) {
        result += ", message: '" + msg + "'";
      }
      return result;
    } else {
      return e.toString();
    }
  }
}
