package jetbrains.buildServer.commitPublisher.tfs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import java.security.KeyStore;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates TFS Git commit statuses via REST API.
 */
class TfsStatusPublisher extends HttpBasedCommitStatusPublisher {

  private static final Logger LOG = Logger.getInstance(TfsStatusPublisher.class.getName());
  private static final String COMMITS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits?api-version=1.0&$top=1";
  private static final String COMMIT_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits/{3}?api-version=1.0";
  private static final String COMMIT_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits/{3}/statuses?api-version=2.1";
  private static final String PULL_REQUEST_ITERATIONS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/iterations?api-version=4.0-preview";
  private static final String PULL_REQUEST_ITERATION_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/iterations/{4}/statuses?api-version=4.0-preview";
  private static final String PULL_REQUEST_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/statuses?api-version=4.0-preview";
  private static final String ERROR_AUTHORIZATION = "Check access token value and verify that it has Code (status) and Code (read) scopes";
  private static final String FAILED_TO_TEST_CONNECTION_TO_REPOSITORY = "TFS publisher has failed to test connection to repository ";
  private static final Gson myGson = new Gson();
  private final WebLinks myLinks;

  // Captures pull request identifier. Example: refs/pull/1/merge
  private static final Pattern TFS_GIT_PULL_REQUEST_PATTERN = Pattern.compile("^refs\\/pull\\/(\\d+)");

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

  @Override
  public void processResponse(@NotNull final HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    if (response.getStatusCode() >= 400) {
      processErrorResponse(response);
    }
  }

  public static void testConnection(@NotNull final VcsRoot root,
                                    @NotNull final Map<String, String> params,
                                    @NotNull final String commitId,
                                    @Nullable final KeyStore trustStore) throws PublisherException {
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      throw new PublisherException("Status publisher supports only Git VCS roots");
    }

    final TfsRepositoryInfo info = getServerAndProject(root);
    try {
      final String url = MessageFormat.format(COMMIT_STATUS_URL_FORMAT,
        info.getServer(), info.getProject(), info.getRepository(), commitId);
      HttpHelper.post(url, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN), StringUtil.EMPTY, ContentType.DEFAULT_TEXT,
        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                      trustStore, new DefaultHttpResponseProcessor() {
          @Override
          public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
            final int status = response.getStatusCode();
            if (status == 401 || status == 403) {
              throw new HttpPublisherException(ERROR_AUTHORIZATION);
            }

            // Ignore Bad Request for POST check
            if (status == 400) {
              return;
            }

            if (status != 200) {
              processErrorResponse(response);
            }
          }
        });
    } catch (Exception e) {
      final String message = FAILED_TO_TEST_CONNECTION_TO_REPOSITORY + info;
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }
  }

  @Nullable
  public static String getLatestCommitId(@NotNull final VcsRoot root,
                                         @NotNull final Map<String, String> params,
                                         @Nullable final KeyStore trustStore) throws PublisherException {
    final TfsRepositoryInfo info = getServerAndProject(root);
    final String url = MessageFormat.format(COMMITS_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository());
    final String[] commitId = {null};

    try {
      HttpHelper.get(url, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN),
        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                     trustStore, new DefaultHttpResponseProcessor() {
          @Override
          public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
            super.processResponse(response);

            CommitsList commits = processGetResponse(response, CommitsList.class);
            if (commits == null || commits.value == null || commits.value.size() == 0) {
              throw new HttpPublisherException("No commits are available in repository %s" + info);
            }

            commitId[0] = commits.value.get(0).commitId;
          }
        });
    } catch (Exception e) {
      final String message = FAILED_TO_TEST_CONNECTION_TO_REPOSITORY + info;
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }

    return commitId[0];
  }

  @NotNull
  private static List<String> getParentCommits(@NotNull final TfsRepositoryInfo info,
                                               @NotNull final String parentCommitId,
                                               @NotNull final Map<String, String> params,
                                               @Nullable final KeyStore trustStore) throws PublisherException {
    final String url = MessageFormat.format(COMMIT_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), parentCommitId);
    final List<String> commits = new ArrayList<String>();

    try {
      HttpHelper.get(url, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN),
        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                     trustStore, new DefaultHttpResponseProcessor() {
          @Override
          public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
            super.processResponse(response);

            Commit commit = processGetResponse(response, Commit.class);
            if (commit == null) {
              throw new HttpPublisherException(String.format("Commit %s is not available in repository %s",
                parentCommitId, info)
              );
            }

            if (commit.parents != null) {
              commits.addAll(commit.parents);
            }
          }
        });
    } catch (Exception e) {
      final String message = "TFS publisher has failed to get parent commits in repository " + info;
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }

    return commits;
  }

  @Nullable
  private static String getPullRequestIteration(@NotNull final TfsRepositoryInfo info,
                                                @NotNull final String pullRequestId,
                                                @NotNull final List<String> commits,
                                                @NotNull final Map<String, String> params,
                                                @Nullable final KeyStore trustStore) throws PublisherException {
    final String url = MessageFormat.format(PULL_REQUEST_ITERATIONS_URL_FORMAT,
      info.getServer(), info.getProject(), info.getRepository(), pullRequestId);
    final String[] iterationId = {null};

    try {
      HttpHelper.get(url, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN),
        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                     trustStore, new DefaultHttpResponseProcessor() {
          @Override
          public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
            super.processResponse(response);

            IterationsList iterations = processGetResponse(response, IterationsList.class);
            if (iterations == null || iterations.value == null || iterations.value.size() == 0) {
              LOG.debug("No iterations are available in repository " + info);
              return;
            }

            for (Iteration iteration : iterations.value) {
              final IterationCommit commitRef = iteration.sourceRefCommit;
              if (commitRef != null && CollectionsUtil.findFirst(commits, new Filter<String>() {
                @Override
                public boolean accept(@NotNull String commit) {
                  return commit.equals(commitRef.commitId);
                }
              }) != null) {
                iterationId[0] = iteration.id;
                break;
              }
            }
          }
        });
    } catch (Exception e) {
      final String message = String.format("Unable to get pull request %s iterations in repository %s", pullRequestId, info);
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }

    return iterationId[0];
  }

  private static <T> T processGetResponse(@NotNull final HttpHelper.HttpResponse response, @NotNull final Class<T> type) throws HttpPublisherException, IOException {
    final int status = response.getStatusCode();
    if (status == 401 || status == 403) {
      throw new HttpPublisherException(ERROR_AUTHORIZATION);
    }

    if (status != 200) {
      processErrorResponse(response);
    }

    final String content = response.getContent();
    if (null == content) {
      throw new HttpPublisherException("TFS publisher has received no response");
    }
    try {
      return myGson.fromJson(content, type);
    } catch (JsonSyntaxException e) {
      throw new HttpPublisherException("Invalid response while listing latest commits: " + e.getMessage(), e);
    }
  }

  private static void processErrorResponse(@NotNull final HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    final int status = response.getStatusCode();
    final String content = response.getContent();
    if (null == content) {
      throw new HttpPublisherException(status, response.getStatusText(), "Empty HTTP response");
    }

    Error error = null;
    try {
      error = myGson.fromJson(content, Error.class);
    } catch (JsonSyntaxException e) {
      // Invalid JSON response
    }

    final String message;
    if (error != null && error.message != null) {
      message = error.message;
    } else {
      message = "HTTP response error";
    }

    throw new HttpPublisherException(status, response.getStatusText(), message);
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final VcsRoot root = revision.getRoot();
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      LOG.warn("No revisions were found to update TFS Git commit status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    final TfsRepositoryInfo info = getServerAndProject(root);
    final CommitStatus status = getCommitStatus(build, isStarting);
    final String data = myGson.toJson(status);

    final String commitId = revision.getRevision();
    final String commitStatusUrl = MessageFormat.format(COMMIT_STATUS_URL_FORMAT,
      info.getServer(), info.getProject(), info.getRepository(), commitId);
    postAsync(commitStatusUrl, StringUtil.EMPTY, myParams.get(TfsConstants.ACCESS_TOKEN),
      data, ContentType.APPLICATION_JSON,
      Collections.singletonMap("Accept", "application/json"),
      LogUtil.describe(build));

    // Check whether pull requests status publishing enabled
    final String publishPullRequest = StringUtil.emptyIfNull(myParams.get(TfsConstants.PUBLISH_PULL_REQUESTS)).trim();
    if (!Boolean.valueOf(publishPullRequest)) {
      return;
    }

    // Get branch and try to find pull request id
    final String branch = revision.getRepositoryVersion().getVcsBranch();
    if (StringUtil.isEmptyOrSpaces(branch)) {
      LOG.debug(String.format("Branch was not specified for commit %s, pull request status would not be published", commitId));
      return;
    }

    final Matcher matcher = TFS_GIT_PULL_REQUEST_PATTERN.matcher(branch);
    if (!matcher.find()) {
      LOG.debug(String.format("Branch %s for commit %s does not contain info about pull request, status would not be published", branch, commitId));
      return;
    }

    final String pullRequestId = matcher.group(1);

    final KeyStore trustStore = getSettings().trustStore();

    // Since it's a merge request we need to get parent commit for it
    final List<String> commits = getParentCommits(info, commitId, myParams, trustStore);

    // Then we need to get pull request iteration where this commit present
    final String iterationId = getPullRequestIteration(info, pullRequestId, commits, myParams, trustStore);
    final String pullRequestStatusUrl;

    if (StringUtil.isEmptyOrSpaces(iterationId)) {
      // Publish status for pull request
      pullRequestStatusUrl = MessageFormat.format(PULL_REQUEST_STATUS_URL_FORMAT,
        info.getServer(), info.getProject(), info.getRepository(), pullRequestId);
    } else {
      // Publish status for pull request iteration
      pullRequestStatusUrl = MessageFormat.format(PULL_REQUEST_ITERATION_STATUS_URL_FORMAT,
        info.getServer(), info.getProject(), info.getRepository(), pullRequestId, iterationId);
    }

    postAsync(pullRequestStatusUrl, StringUtil.EMPTY, myParams.get(TfsConstants.ACCESS_TOKEN),
      data, ContentType.APPLICATION_JSON,
      Collections.singletonMap("Accept", "application/json"),
      LogUtil.describe(build)
    );
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
    context.name = build.getBuildTypeExternalId();
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

  @NotNull
  private static TfsRepositoryInfo getServerAndProject(VcsRoot root) throws PublisherException {
    final String url = root.getProperty("url");
    final TfsRepositoryInfo info = TfsRepositoryInfo.parse(url);
    if (info == null) {
      throw new PublisherException(String.format("Invalid URL for TFS Git project '%s'. Publisher supports only TFS servers", url));
    }

    return info;
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

  private static class CommitsList {
    private List<Commit> value;
  }

  private static class Commit {
    private String commitId;
    private List<String> parents;
  }

  private static class IterationsList {
    private List<Iteration> value;
  }

  private static class Iteration {
    private String id;
    private IterationCommit sourceRefCommit;
  }

  private static class IterationCommit {
    private String commitId;
  }
}
