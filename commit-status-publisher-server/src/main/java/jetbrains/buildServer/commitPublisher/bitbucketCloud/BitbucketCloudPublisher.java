package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.VcsRootInstance;
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

public class BitbucketCloudPublisher extends BaseCommitStatusPublisher {
  private static final Logger LOG = Logger.getInstance(BitbucketCloudPublisher.class.getName());

  private final WebLinks myLinks;

  public BitbucketCloudPublisher(@NotNull WebLinks links,
                                 @NotNull Map<String, String> params) {
    super(params);
    myLinks = links;
  }

  @NotNull
  public String toString() {
    return "bitbucketCloud";
  }

  public void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, BitbucketCloudBuildStatus.INPROGRESS, "Build started");
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    BitbucketCloudBuildStatus status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.SUCCESSFUL : BitbucketCloudBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(build, revision, status, description);
  }

  @Override
  public void buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) {
    BitbucketCloudBuildStatus status;
    if (buildInProgress) {
      status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.INPROGRESS : BitbucketCloudBuildStatus.FAILED;
    } else {
      status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.SUCCESSFUL : BitbucketCloudBuildStatus.FAILED;
    }
    String description = build.getStatusDescriptor().getText();
    if (user != null && comment != null) {
      description += " with a comment by " + user.getExtendedName() + ": \"" + comment + "\"";
    }
    vote(build, revision, status, description);
  }

  @Override
  public void buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, BitbucketCloudBuildStatus.SUCCESSFUL, "Build marked as successful");
  }

  @Override
  public void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, BitbucketCloudBuildStatus.FAILED, build.getStatusDescriptor().getText());
  }

  @Override
  public void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, BitbucketCloudBuildStatus.FAILED, build.getStatusDescriptor().getText());
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull BitbucketCloudBuildStatus status,
                    @NotNull String comment) {
    String msg = createMessage(status, build.getBuildPromotion().getBuildTypeId(), getBuildName(build), myLinks.getViewResultsUrl(build), comment);
    try {
      final VcsRootInstance root = revision.getRoot();
      Repository repository = BitbucketCloudRepositoryParser.parseRepository(root);
      if (repository == null)
        throw new Exception("Cannot parse repository from VCS root url " + root);
      vote(revision.getRevision(), msg, repository);
    } catch (Exception e) {
      reportProblem(build.getBuildPromotion(), revision, e);
    }
  }

  private void reportProblem(final BuildPromotion buildPromotion,
                             final BuildRevision revision,
                             final Exception e) {
    String logMessage;
    String problemText;
    if (e instanceof BitBucketException) {
      BitBucketException se = (BitBucketException) e;
      logMessage = "Error while publishing commit status to Bitbucket Cloud for promotion " + LogUtil.describe(buildPromotion) +
              ", response code: " + se.getStatusCode() +
              ", reason: " + se.getReason();
      problemText = "Error while publishing commit status to Bitbucket Cloud, response code: " + se.getStatusCode() +
              ", reason: " + se.getReason();
      String msg = se.getStashMessage();
      if (msg != null) {
        logMessage += ", message: '" + msg + "'";
        problemText += ", message: '" + msg + "'";
      }
    } else {
      logMessage = "Error while publishing commit status to Bitbucket Cloud for promotion " + LogUtil.describe(buildPromotion) +
              " " + e.toString();
      problemText = "Error while publishing commit status to Bitbucket Cloud: " + e.toString();
    }
    LOG.info(logMessage);
    LOG.debug(logMessage, e);

    String problemId = "bitbucketCloud.publisher." + revision.getRoot().getId();
    BuildProblemData buildProblem = BuildProblemData.createBuildProblem(problemId, "bitbucketCloud.publisher", problemText);
    ((BuildPromotionEx)buildPromotion).addBuildProblem(buildProblem);
  }

  @NotNull
  private String createMessage(@NotNull BitbucketCloudBuildStatus status,
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

  private void vote(@NotNull String commit, @NotNull String data, @NotNull Repository repository) throws URISyntaxException, IOException,
          UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, BitBucketException {
    URI bitBucketURI = new URI(getBaseUrl());


    LOG.debug(getBaseUrl() + " :: " + commit + " :: " + data);

    DefaultHttpClient client = new DefaultHttpClient();
    HttpPost post = null;
    HttpResponse response = null;
    try {
      client.getCredentialsProvider().setCredentials(
              new AuthScope(bitBucketURI.getHost(), bitBucketURI.getPort()),
              new UsernamePasswordCredentials(getUsername(), getPassword()));

      AuthCache authCache = new BasicAuthCache();
      authCache.put(new HttpHost(bitBucketURI.getHost(), bitBucketURI.getPort(), bitBucketURI.getScheme()), new BasicScheme());
      BasicHttpContext ctx = new BasicHttpContext();
      ctx.setAttribute(ClientContext.AUTH_CACHE, authCache);

      String url = getBaseUrl() + "2.0/repositories/" + repository.owner() + "/" + repository.repositoryName() + "/commit/" + commit + "/statuses/build";

      LOG.debug(url);

      post = new HttpPost(url);
      post.setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));
      response = client.execute(post, ctx);

      StatusLine statusLine = response.getStatusLine();
      LOG.debug("Response Status Code was " + statusLine.getStatusCode());
      if (statusLine.getStatusCode() >= 400)
        throw new BitBucketException(statusLine.getStatusCode(), statusLine.getReasonPhrase(), parseErrorMessage(response));
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
      LOG.debug("Bitbucket Cloud response: " + str);
      JsonElement json = new JsonParser().parse(str);
      if (!json.isJsonObject())
        return null;
      JsonObject jsonObj = json.getAsJsonObject();
      JsonElement error = jsonObj.get("error");
      if (error == null || !error.isJsonObject())
        return null;

      final JsonObject errorObj = error.getAsJsonObject();
      JsonElement msg = errorObj.get("message");
      if (msg == null)
        return null;
      StringBuilder result = new StringBuilder(msg.getAsString());
      JsonElement fields = errorObj.get("fields");
      if (fields != null && fields.isJsonObject()) {
        result.append(". ");
        JsonObject fieldsObj = fields.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : fieldsObj.entrySet()) {
          result.append("Field '").append(e.getKey()).append("': ").append(e.getValue().getAsString());
        }
      }
      return result.toString();
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

  String getBaseUrl() { return "https://api.bitbucket.org/";  }

  private String getUsername() {
    return myParams.get(Constants.BITBUCKET_CLOUD_USERNAME);
  }

  private String getPassword() {
    return myParams.get(Constants.BITBUCKET_CLOUD_PASSWORD);
  }


  private static class BitBucketException extends Exception {
    private final int myStatusCode;
    private final String myReason;
    private final String myMessage;
    public BitBucketException(int statusCode, String reason, @Nullable String message) {
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
