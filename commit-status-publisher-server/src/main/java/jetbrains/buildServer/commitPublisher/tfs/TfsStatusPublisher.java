package jetbrains.buildServer.commitPublisher.tfs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates TFS Git commit statuses via REST API.
 */
class TfsStatusPublisher extends HttpBasedCommitStatusPublisher {

  private static final Logger LOG = Logger.getInstance(TfsStatusPublisher.class.getName());
  private static final String COMMITS_URL_FORMAT = "%s/_apis/git/repositories/%s/commits?api-version=1.0&$top=1";
  private static final String STATUS_URL_FORMAT = "%s/_apis/git/repositories/%s/commits/%s/statuses?api-version=2.1";
  private static final Gson myGson = new Gson();

  // Captures following groups: (server url + collection) (/_git/) (git project name)
  // Example: (http://localhost:81/tfs/collection) (/_git/) (git_project)
  private static final Pattern TFS_GIT_PROJECT_PATTERN = Pattern.compile("(https?\\:\\/\\/.+)(\\/_git\\/)([^\\.\\/\\?]+)");
  private final WebLinks myLinks;

  TfsStatusPublisher(@NotNull final CommitStatusPublisherSettings settings,
                     @NotNull final SBuildType buildType,
                     @NotNull final String buildFeatureId,
                     @NotNull final ExecutorServices executorServices,
                     @NotNull final WebLinks webLinks,
                     @NotNull final Map<String, String> params,
                     @NotNull final CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, executorServices, params, problems);
    myLinks = webLinks;
  }

  @NotNull
  public String toString() {
    return TfsConstants.ID;
  }

  @NotNull
  @Override
  public String getId() {
    return TfsConstants.ID;
  }

  @Override
  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, true);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull final SBuild build, @NotNull final BuildRevision revision, final boolean buildInProgress) throws PublisherException {
    updateBuildStatus(build, revision, buildInProgress);
    return true;
  }

  public static void testConnection(VcsRoot root, Map<String, String> params) throws PublisherException {
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      throw new PublisherException("Status publisher supports only Git VCS roots");
    }

    final Pair<String, String> settings = getServerAndProject(root);
    final String url = String.format(COMMITS_URL_FORMAT, settings.first, settings.second);
    try {
      final HttpResponseProcessor processor = new DefaultHttpResponseProcessor() {
        @Override
        public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {
          final int status = response.getStatusLine().getStatusCode();
          if (status == 401) {
            throw new HttpPublisherException("Invalid credentials: check access token");
          } else if (status == 403) {
            throw new HttpPublisherException("Invalid credentials: check access token scopes");
          }

          final HttpEntity entity = response.getEntity();
          if (null == entity) {
            throw new HttpPublisherException("TFS publisher has received no response");
          }

          final String json = EntityUtils.toString(entity);
          if (status != 200) {
            Error error = null;
            try {
              error = myGson.fromJson(json, Error.class);
            } catch (JsonSyntaxException e) {
              // Invalid JSON response
            }

            final String message;
            if (error != null && error.message != null) {
              message = error.message + ": HTTP response code " + status;
            } else {
              message = "Invalid HTTP response code " + status;
            }

            throw new HttpPublisherException(message);
          }
        }
      };

      HttpHelper.get(url, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN),
        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, processor);
    } catch (Exception e) {
      throw new PublisherException(String.format("TFS publisher has failed to connect to repository %s/_git/%s", settings.first, settings.second), e);
    }
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final VcsRoot root = revision.getRoot();
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      LOG.warn("No revisions were found to update TFS Git commit status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    final Pair<String, String> settings = getServerAndProject(root);
    final String url = String.format(STATUS_URL_FORMAT, settings.first, settings.second, revision.getRevision());
    final CommitStatus status = getCommitStatus(build, isStarting);
    final String data = myGson.toJson(status);

    postAsync(url, StringUtil.EMPTY, myParams.get(TfsConstants.ACCESS_TOKEN),
      data, ContentType.APPLICATION_JSON,
      Collections.singletonMap("Accept", "application/json"),
      LogUtil.describe(build));
  }

  @NotNull
  private CommitStatus getCommitStatus(final SBuild build, final boolean isStarting) {
    final CommitStatus status = new CommitStatus();

    final StatusState state = getState(isStarting, build.getBuildStatus());
    status.state = state;
    status.description = String.format("The build %s #%s %s %s",
      build.getFullName(), build.getBuildNumber(),
      isStarting ? "is" : "has", state.toString().toLowerCase());
    status.targetURL = myLinks.getViewResultsUrl(build);

    final StatusContext context = new StatusContext();
    context.name = String.format("%s/%s", build.getBuildTypeExternalId(), build.getBuildId());
    context.genre = "TeamCity";
    status.context = context;

    return status;
  }

  private static StatusState getState(boolean isStarting, Status status) {
    if (!isStarting) {
      if (status.isSuccessful()) return StatusState.Succeeded;
      else if (status == Status.ERROR) return StatusState.Error;
      else if (status == Status.FAILURE) return StatusState.Failed;
    }

    return StatusState.Pending;
  }

  private static Pair<String, String> getServerAndProject(VcsRoot root) throws PublisherException {
    final String url = root.getProperty("url");
    if (StringUtil.isEmptyOrSpaces(url)) {
      throw new PublisherException(String.format("Invalid Git VCS root '%s' settings, missing Server URL property", root.getName()));
    }

    final Matcher matcher = TFS_GIT_PROJECT_PATTERN.matcher(url);
    if (!matcher.find()) {
      throw new PublisherException(String.format("Invalid Git server URL '%s'. Publisher supports only TFS servers", url));
    }

    final String projectName = matcher.group(3);
    String serverName = matcher.group(1);
    if (StringUtil.endsWithIgnoreCase(serverName, "DefaultCollection")) {
      serverName += "/" + projectName;
    }

    return Pair.create(serverName, projectName);
  }

  private static class Error {
    private String message;
  }

  private static class CommitStatus {
    private StatusState state;
    private String description;
    private String targetURL;
    private StatusContext context;
  }

  private static enum StatusState {
    Pending, Succeeded, Failed, Error
  }

  private static class StatusContext {
    private String name;
    private String genre;
  }
}
