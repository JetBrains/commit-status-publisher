package jetbrains.buildServer.commitPublisher.stash;

import com.google.common.collect.Iterables;
import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.stash.data.PullRequestInfo;
import jetbrains.buildServer.commitPublisher.stash.data.StashCommit;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class StashPublisher extends HttpBasedCommitStatusPublisher {
  public static final String PUBLISH_QUEUED_BUILD_STATUS = "teamcity.stashCommitStatusPublisher.publishQueuedBuildStatus";

  private static final Logger LOG = Logger.getInstance(StashPublisher.class.getName());

  protected final Gson myGson = new Gson();

  private final WebLinks myLinks;

  StashPublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull final ExecutorServices executorServices,
                 @NotNull WebLinks links, @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, executorServices, params, problems);
    myLinks = links;
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
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) {
    vote(build, revision, buildInProgress ? StashBuildStatus.INPROGRESS : StashBuildStatus.SUCCESSFUL, "Build marked as successful");
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
    String commitRevision = revision.getRevision();
    try {
      StashCommit commit = findPullRequestCommit(build);
      if (commit != null) {
        commitRevision = commit.id;
      }
    } catch (PublisherException e) {
      e.printStackTrace();
    }
    vote(commitRevision, msg, LogUtil.describe(build));
  }

  private void vote(@NotNull SQueuedBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    String msg = createMessage(status, build.getBuildPromotion().getBuildTypeExternalId(), getBuildName(build), myLinks.getQueuedBuildUrl(build), comment);
    vote(revision.getRevision(), msg, LogUtil.describe(build));
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

  private void vote(@NotNull String commit, @NotNull String data, @NotNull String buildDescription) {
    String url = getBaseUrl() + "/rest/build-status/1.0/commits/" + commit;
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

  @NotNull
  private String getBuildName(@NotNull SBuild build) {
    return build.getFullName() + " #" + build.getBuildNumber();
  }

  @NotNull
  private String getBuildName(@NotNull SQueuedBuild build) {
    return build.getBuildType().getName();
  }

  private String getBaseUrl() {
    return myParams.get(Constants.STASH_BASE_URL);
  }

  private String getUsername() {
    return myParams.get(Constants.STASH_USERNAME);
  }

  private String getPassword() {
    return myParams.get(Constants.STASH_PASSWORD);
  }

  private StashCommit findPullRequestCommit(@NotNull SBuild build) throws PublisherException {
    String apiUrl = myParams.get(Constants.STASH_BASE_URL);
    String projectKey = myParams.get(Constants.STASH_PROJECT_KEY);
    String repository = myParams.get(Constants.STASH_REPO_NAME);

    final List<StashCommit> lastCommit = new ArrayList<StashCommit>();

    // Plugin will recognize pull request only when Branch specification parameter will be configured as below:
    // +:refs/pull-requests/(*/merge)
    // https://blog.jetbrains.com/teamcity/2013/02/automatically-building-pull-requests-from-github-with-teamcity

    if (build.getBranch().getName().contains("merge")) { // try to find pull request only for branches with merge in the name
      String pullRequestNumber = build.getBranch().getName().replace("/merge",""); //remove merge from brache name to get only pull request number
      apiUrl = apiUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repository + "/pull-requests/" + pullRequestNumber + "/commits/";
      LOG.debug("Bitbucket Pull Request apiUrl: " + apiUrl);
    }

    if (null != apiUrl || apiUrl.length() != 0) {
      try {
        HttpResponseProcessor processor = new DefaultHttpResponseProcessor() {
          @Override
          public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {

            final HttpEntity entity = response.getEntity();
            if (null == entity) {
              throw new HttpPublisherException("Bitbucket publisher has received no response");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            entity.writeTo(bos);
            final String json = bos.toString("utf-8");
            PullRequestInfo commitInfo = myGson.fromJson(json, PullRequestInfo.class);
            if (null == commitInfo)
              throw new HttpPublisherException("Bitbucket Server publisher has received a malformed response");
            if (null != commitInfo.values && !commitInfo.values.isEmpty()) {
              StashCommit commit = Iterables.get(commitInfo.values, 0);
              if (commit != null) {
                lastCommit.add(commit);
              }
            }
          }
        };

        HttpHelper.get(apiUrl, getUsername(), getPassword(),
                Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, processor);
      } catch (Exception ex) {
        throw new PublisherException(String.format("Bitbucket Server publisher has failed to connect to %s repository", apiUrl), ex);
      }
    }
    if (!lastCommit.isEmpty() && lastCommit.size() > 0) {
      return lastCommit.get(0);
    }
    return null;
  }
}
