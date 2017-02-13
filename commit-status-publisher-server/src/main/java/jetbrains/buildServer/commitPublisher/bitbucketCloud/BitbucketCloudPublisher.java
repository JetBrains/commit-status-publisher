package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

class BitbucketCloudPublisher extends HttpBasedCommitStatusPublisher {
  private static final Logger LOG = Logger.getInstance(BitbucketCloudPublisher.class.getName());
  private String myBaseUrl = BitbucketCloudSettings.DEFAULT_API_URL;
  private final WebLinks myLinks;

  BitbucketCloudPublisher(@NotNull CommitStatusPublisherSettings settings,
                          @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                          @NotNull final ExecutorServices executorServices,
                          @NotNull WebLinks links,
                          @NotNull Map<String, String> params,
                          @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, executorServices, params, problems);
    myLinks = links;
  }

  @NotNull
  public String toString() {
    return "bitbucketCloud";
  }

  @Override
  @NotNull
  public String getId() {
    return Constants.BITBUCKET_PUBLISHER_ID;
  }

  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) throws PublisherException {
    vote(build, revision, BitbucketCloudBuildStatus.INPROGRESS, "Build started");
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    BitbucketCloudBuildStatus status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.SUCCESSFUL : BitbucketCloudBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(build, revision, status, description);
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) throws
                                                                                                                                                                PublisherException {
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
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    vote(build, revision, buildInProgress ? BitbucketCloudBuildStatus.INPROGRESS : BitbucketCloudBuildStatus.SUCCESSFUL, "Build marked as successful");
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    vote(build, revision, BitbucketCloudBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) throws PublisherException {
    vote(build, revision, BitbucketCloudBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull BitbucketCloudBuildStatus status,
                    @NotNull String comment) throws PublisherException {
    String msg = createMessage(status, build.getBuildPromotion().getBuildTypeId(), getBuildName(build), myLinks.getViewResultsUrl(build), comment);
    final VcsRootInstance root = revision.getRoot();
    Repository repository = BitbucketCloudRepositoryParser.parseRepository(root);
    if (repository == null) {
      throw new PublisherException(String.format("Bitbucket publisher has failed to parse repository URL from VCS root '%s'", root.getName()));
    }
    vote(revision.getRevision(), msg, repository, LogUtil.describe(build));
  }

  @NotNull
  private String createMessage(@NotNull BitbucketCloudBuildStatus status,
                               @NotNull String id,
                               @NotNull String name,
                               @NotNull String url,
                               @NotNull String description) {
    final StringBuilder data = new StringBuilder();
    data.append("{")
            .append("\"state\":").append("\"").append(status).append("\",")
            .append("\"key\":").append("\"").append(id).append("\",")
            .append("\"name\":").append("\"").append(name).append("\",")
            .append("\"url\":").append("\"").append(url).append("\",")
            .append("\"description\":").append("\"").append(escape(description)).append("\"")
            .append("}");
    return data.toString();
  }

  private void vote(@NotNull String commit, @NotNull String data, @NotNull Repository repository, @NotNull String buildDescription) {
    LOG.debug(getBaseUrl() + " :: " + commit + " :: " + data);
    String url = getBaseUrl() + "2.0/repositories/" + repository.owner() + "/" + repository.repositoryName() + "/commit/" + commit + "/statuses/build";
    postAsync(url, getUsername(), getPassword(), data, ContentType.APPLICATION_JSON, null, buildDescription);
  }


  @Override
  public void processResponse(HttpResponse response) throws HttpPublisherException {
    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() >= 400)
      throw new HttpPublisherException(statusLine.getStatusCode(), statusLine.getReasonPhrase(), parseErrorMessage(response));
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

  @NotNull
  private String getBuildName(@NotNull SBuild build) {
    return build.getFullName() + " #" + build.getBuildNumber();
  }

  private String getBaseUrl() { return myBaseUrl;  }

  /**
   * Used for testing only at the moments
   * @param url - new base URL to replace the default one
   */
  void setBaseUrl(String url) { myBaseUrl = url; }

  private String getUsername() {
    return myParams.get(Constants.BITBUCKET_CLOUD_USERNAME);
  }

  private String getPassword() {
    return myParams.get(Constants.BITBUCKET_CLOUD_PASSWORD);
  }

}
