package jetbrains.buildServer.commitPublisher.deveo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.PublishError;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
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

public class DeveoPublisher extends BaseCommitStatusPublisher {
  private static final Logger LOG = Logger.getInstance(DeveoPublisher.class.getName());

  private final WebLinks myLinks;

  public DeveoPublisher(@NotNull WebLinks links,
                                 @NotNull Map<String, String> params) {
    super(params);
    myLinks = links;
  }

  @NotNull
  public String toString() {
    return "deveo";
  }

  @Override
  public String getId() {
    return Constants.DEVEO_PUBLISHER_ID;
  }

  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    DeveoBuildStatus status = build.getBuildStatus().isSuccessful() ? DeveoBuildStatus.SUCCESSFUL : DeveoBuildStatus.FAILED;
    vote(build, revision, status);
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) {
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, DeveoBuildStatus.SUCCESSFUL);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, DeveoBuildStatus.FAILED);
    return true;
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull DeveoBuildStatus status) {
    try {
      final VcsRootInstance root = revision.getRoot();
      Repository repository = DeveoRepositoryParser.parseRepository(root);
      if (repository == null) {
        throw new PublishError("Cannot parse repository from VCS root url " + root.getName());
      }
      String msg = createMessage(status, repository.owner(), repository.repositoryName(), build.getFullName(), revision.getRevision(), myLinks.getViewResultsUrl(build));
      vote(msg);
    } catch (Exception e) {
      throw new PublishError("Cannot publish status to Deveo for VCS root " +
              revision.getRoot().getName() + ": " + getMessage(e), e);
    }
  }

  @NotNull
  private String createMessage(@NotNull DeveoBuildStatus status,
                               @NotNull String project,
                               @NotNull String repository,
                               @NotNull String build,
                               @NotNull String commit,
                               @NotNull String url) {
    StringBuilder data = new StringBuilder();
    data.append("{ ")
            .append("\"target\": ").append("\"build").append("\", ")
            .append("\"operation\": ").append("\"").append(getDeveoStatusText(status)).append("\", ")
            .append("\"project\": ").append("\"").append(project).append("\", ")
            .append("\"repository\": ").append("\"").append(repository).append("\", ")
            .append("\"name\": ").append("\"").append(build).append("\", ")
            .append("\"commits\": ").append("[\"" + commit + "\"]").append(", ")
            .append("\"resources\": ").append("[\"").append(url).append("\"] ")
            .append("}");
    LOG.debug("DATA: " + data.toString());
    return data.toString();
  }

  private String getDeveoStatusText(DeveoBuildStatus status) {
    if (status == DeveoBuildStatus.SUCCESSFUL) {
      return "completed";
    }
    return "failed";
  }

  private void vote(@NotNull String data) throws URISyntaxException, IOException,
          UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, DeveoException {

    URI deveoURI = new URI(getDeveoEventsApiURL());

    LOG.debug(getApiHostname() + " :: " + data + " :: " + String.valueOf(deveoURI));

    HttpClient client = HttpClientBuilder.create().build();
    HttpResponse response = null;

    HttpPost request = getDeveoHttpPostRequest(data, deveoURI);
    try {
      response = client.execute(request);
      StatusLine statusLine = response.getStatusLine();
      LOG.debug("Response Status Code was " + statusLine.getStatusCode());
      if (statusLine.getStatusCode() >= 400)
        throw new DeveoException(statusLine.getStatusCode(), statusLine.getReasonPhrase(), parseErrorMessage(response));
    } finally {
      HttpClientUtils.closeQuietly(response);
      releaseConnection(request);
      HttpClientUtils.closeQuietly(client);
    }
  }

  @NotNull
  private HttpPost getDeveoHttpPostRequest(@NotNull String data, URI deveoURI) {
    HttpPost request = new HttpPost(deveoURI);
    request.setHeader(HttpHeaders.ACCEPT, "application/vnd.deveo.v1");
    request.setHeader(HttpHeaders.AUTHORIZATION, "deveo plugin_key='" + getPluginKey() + "',company_key='" +
            getCompanyKey() + "',account_key='" + getAccountKey() + "'");
    request.setHeader(HttpHeaders.CONTENT_TYPE, String.valueOf(ContentType.APPLICATION_JSON));
    request.setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));
    return request;
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
      LOG.debug("Deveo response: " + str);
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

  private String getCompanyKey() {
    return myParams.get(Constants.DEVEO_COMPANY_KEY);
  }

  private String getApiHostname() { return myParams.get(Constants.DEVEO_API_HOSTNAME); }

  private String getPluginKey() { return myParams.get(Constants.DEVEO_PLUGIN_KEY); }

  private String getAccountKey() {
    return myParams.get(Constants.DEVEO_ACCOUNT_KEY);
  }

  private String getDeveoEventsApiURL() {
    StringBuilder sb = new StringBuilder(getApiHostname());
    if (!sb.toString().endsWith("/")) {
      sb.append("/");
    }
    return sb.toString() + "api/events";
  }

  private static class DeveoException extends Exception {
    private final int myStatusCode;
    private final String myReason;
    private final String myMessage;
    public DeveoException(int statusCode, String reason, @Nullable String message) {
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
    public String getDeveoMessage() {
      return myMessage;
    }
  }

  @NotNull
  private String getMessage(@NotNull Exception e) {
    if (e instanceof DeveoException) {
      DeveoException se = (DeveoException) e;
      String result = "response code: " + se.getStatusCode() + ", reason: " + se.getReason();
      String msg = se.getDeveoMessage();
      if (msg != null) {
        result += ", message: '" + msg + "'";
      }
      return result;
    } else {
      return e.toString();
    }
  }
}
