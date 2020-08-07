package jetbrains.buildServer.commitPublisher.stash;

import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import java.util.LinkedHashMap;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.VersionComparatorUtil;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class StashPublisher extends HttpBasedCommitStatusPublisher {
  public static final String PROP_PUBLISH_QUEUED_BUILD_STATUS = "teamcity.stashCommitStatusPublisher.publishQueuedBuildStatus";

  private static final Logger LOG = Logger.getInstance(StashPublisher.class.getName());
  private final Gson myGson = new Gson();
  private final WebLinks myLinks;
  private final BuildApiEndpoint myBuildApiEndpoint;

  StashPublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull final ExecutorServices executorServices,
                 @NotNull WebLinks links, @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, executorServices, params, problems);
    myLinks = links;
    myBuildApiEndpoint = new BuildApiEndpoint();
  }

  @NotNull
  public String toString() {
    return "stash";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.STASH_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.INPROGRESS, "Build queued");
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
    StringBuilder description = new StringBuilder("Build removed from queue");
    if (user != null)
      description.append(" by ").append(user.getName());
    if (comment != null)
      description.append(" with comment \"").append(comment).append("\"");
    vote(build, revision, StashBuildStatus.FAILED, description.toString());
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.INPROGRESS, "Build started");
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) {
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
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) {
    vote(build, revision, buildInProgress ? StashBuildStatus.INPROGRESS : StashBuildStatus.SUCCESSFUL, "Build marked as successful");
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) {
    vote(build, revision, StashBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  // There are two ways in Bitbucket Server to report the build status: the older Build API and a new endpoint in the Core API
  // We will be using the former if the Bitbucket Server version is below 7.4 or not retrieved by any reason
  private boolean useBuildAPI(SBuildType buildType) {
    if (buildType instanceof BuildTypeEx && ((BuildTypeEx)buildType).getBooleanInternalParameter("commitStatusPublisher.enforceDeprecatedAPI"))
      return true;
    // NOTE: compare(null, "7.4") < 0
    return VersionComparatorUtil.compare(getSettings().getServerVersion(getBaseUrl()), "7.4") < 0;
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    SBuildData data = new SBuildData(build, revision, status, comment);
    myBuildApiEndpoint.publish(data, LogUtil.describe(build));
  }

  private void vote(@NotNull SQueuedBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    SQueuedBuildData data = new SQueuedBuildData(build, revision, status, comment);
    myBuildApiEndpoint.publish(data, LogUtil.describe(build));
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400)
      throw new HttpPublisherException(statusCode, response.getStatusText(), parseErrorMessage(response));
  }

  @Nullable
  private String parseErrorMessage(@NotNull HttpHelper.HttpResponse response) {
    try {
      String str = response.getContent();
      if (str == null) {
        return null;
      }
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
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  private String getBaseUrl() {
    return HttpHelper.stripTrailingSlash(myParams.get(Constants.STASH_BASE_URL));
  }

  private String getUsername() {
    return myParams.get(Constants.STASH_USERNAME);
  }

  private String getPassword() {
    return myParams.get(Constants.STASH_PASSWORD);
  }

  private interface StatusData {
    @NotNull String getCommit();
    @NotNull String getState();
    @NotNull String getKey();
    @NotNull String getName();
    @NotNull String getUrl();
    @NotNull String getDescription();
  }

  private abstract class BaseBuildData implements StatusData {
    private final String myCommit;
    private final StashBuildStatus myStatus;
    private final String myDescription;

    BaseBuildData(@NotNull BuildRevision revision, @NotNull StashBuildStatus status, @NotNull String description) {
      myCommit = revision.getRevision();
      myStatus = status;
      myDescription = description;
    }

    @NotNull
    @Override
    public String getCommit() {
      return myCommit;
    }

    @NotNull
    @Override
    public String getState() {
      return myStatus.toString();
    }

    @NotNull
    @Override
    public String getDescription() {
      return myDescription;
    }
  }

  private class SBuildData extends BaseBuildData implements StatusData {

    private final SBuild myBuild;

    SBuildData(@NotNull SBuild build, @NotNull BuildRevision revision, @NotNull StashBuildStatus status, @NotNull String description) {
      super(revision, status, description);
      myBuild = build;
    }

    @NotNull
    @Override
    public String getKey() {
      return myBuild.getBuildPromotion().getBuildTypeExternalId();
    }

    @NotNull
    @Override
    public String getName() {
      return myBuild.getFullName() + " #" + myBuild.getBuildNumber();
    }

    @NotNull
    @Override
    public String getUrl() {
      return myLinks.getViewResultsUrl(myBuild);
    }
  }

  private class SQueuedBuildData extends BaseBuildData implements StatusData {

    private final SQueuedBuild myBuild;

    SQueuedBuildData(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @NotNull StashBuildStatus status, @NotNull String description) {
      super(revision, status, description);
      myBuild = build;
    }

    @NotNull
    @Override
    public String getKey() {
      return myBuild.getBuildPromotion().getBuildTypeExternalId();
    }

    @NotNull
    @Override
    public String getName() {
      return myBuild.getBuildType().getName();
    }

    @NotNull
    @Override
    public String getUrl() {
      return myLinks.getQueuedBuildUrl(myBuild);
    }
  }

  private interface BuildStatusEndpoint {
    void publish(@NotNull StatusData data, @NotNull String buildDescription);
  }

  private abstract class BaseBuildStatusEndpoint implements BuildStatusEndpoint {

    @Override
    public void publish(@NotNull StatusData data, @NotNull String buildDescription) {
      post(getEndpointUrl(data), getUsername(), getPassword(), createMessage(data), ContentType.APPLICATION_JSON, null, buildDescription);
    }

    protected abstract String getEndpointUrl(final StatusData data);

    @NotNull
    protected abstract String createMessage(@NotNull StatusData data);
  }

  private class BuildApiEndpoint extends BaseBuildStatusEndpoint implements BuildStatusEndpoint {

    @Override
    protected String getEndpointUrl(final StatusData data) {
      return getBaseUrl() + "/rest/build-status/1.0/commits/" + data.getCommit();
    }

    @NotNull
    @Override
    protected String createMessage(@NotNull final StatusData data) {
      Map<String, String> jsonData = new LinkedHashMap<String, String>();
      jsonData.put("state", data.getState());
      jsonData.put("key", data.getKey());
      jsonData.put("name", data.getName());
      jsonData.put("url", data.getUrl());
      jsonData.put("description", data.getDescription());
      return myGson.toJson(jsonData);
    }
  }

}
