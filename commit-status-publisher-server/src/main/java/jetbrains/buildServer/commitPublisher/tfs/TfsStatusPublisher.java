/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.tfs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.security.KeyStore;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;


/**
 * Updates TFS Git commit statuses via REST API.
 */
class TfsStatusPublisher extends HttpBasedCommitStatusPublisher<TfsStatusPublisher.StatusState> {
  private static final String COMMITS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits?api-version=1.0&$top={3}";
  private static final String COMMIT_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits/{3}?api-version=1.0";
  private static final String COMMIT_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits/{3}/statuses?api-version=2.1";
  private static final String PULL_REQUEST_ITERATIONS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/iterations?api-version=3.0";
  private static final String PULL_REQUEST_ITERATION_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/iterations/{4}/statuses?api-version=3.0-preview";
  private static final String PULL_REQUEST_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/statuses?api-version=3.0-preview";
  private static final String ERROR_AUTHORIZATION = "Check access token value and verify that it has Code (status) and Code (read) scopes";
  private static final String FAILED_TO_TEST_CONNECTION_TO_REPOSITORY = "Azure DevOps publisher has failed to test connection to repository ";
  private static final Gson myGson = new GsonBuilder()
                                          .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                                          .create();

  // Captures pull request identifier. Example: refs/pull/1/merge
  private static final Pattern TFS_GIT_PULL_REQUEST_PATTERN = Pattern.compile("^refs\\/pull\\/(\\d+)/merge");

  private final CommitStatusesCache<CommitStatus> myStatusesCache;

  TfsStatusPublisher(@NotNull final CommitStatusPublisherSettings settings,
                     @NotNull final SBuildType buildType,
                     @NotNull final String buildFeatureId,
                     @NotNull final WebLinks webLinks,
                     @NotNull final Map<String, String> params,
                     @NotNull final CommitStatusPublisherProblems problems,
                     @NotNull CommitStatusesCache<CommitStatus> statusesCache) {
    super(settings, buildType, buildFeatureId, params, problems, webLinks);
    myStatusesCache = statusesCache;
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
  public boolean isPublishingForRevision(@NotNull final BuildRevision revision) {
    final VcsRoot vcsRoot = revision.getRoot();
    return tryGetServerAndProject(vcsRoot, myParams) != null;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion,
                             @NotNull BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return updateQueuedBuildStatus(buildPromotion, revision, additionalTaskInfo, false);
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion,
                                       @NotNull BuildRevision revision,
                                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return updateQueuedBuildStatus(buildPromotion, revision, additionalTaskInfo, true);
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, true);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
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

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    CommitStatus commitStatus = getCommitStatus(revision, removedBuild.getBuildType());
    return getRevisionStatusForRemovedBuild(removedBuild, commitStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable CommitStatus commitStatus) {
    if (commitStatus == null) return null;
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(getBuildName(removedBuild.getBuildPromotion()), commitStatus.context.name);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType);
  }

  private String getBuildName(BuildPromotion buildPromotion) {
    return buildPromotion.getBuildTypeExternalId();
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    CommitStatus commitStatus = getCommitStatus(revision, buildPromotion.getBuildType());
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  private CommitStatus getCommitStatus(BuildRevision revision, SBuildType buildType) throws PublisherException {
    final String buildTypeExternalId = buildType.getExternalId();
    AtomicReference<PublisherException> exception = new AtomicReference<>(null);
    CommitStatus status = myStatusesCache.getStatusFromCache(revision, buildTypeExternalId, () -> {
      try {
        return loadStatuses(revision, buildTypeExternalId);
      } catch (PublisherException e) {
        exception.set(e);
      }
      return Collections.emptyList();
    }, commitStatus -> commitStatus.context.name);

    if (exception.get() != null) {
      throw exception.get();
    }

    return status;
  }

  private Collection<CommitStatus> loadStatuses(@NotNull BuildRevision revision, @NotNull String targetBuildName) throws PublisherException {
    final TfsRepositoryInfo info = getServerAndProject(revision.getRoot(), myParams);
    final String baseUrl = MessageFormat.format(COMMIT_STATUS_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), revision.getRevision());
    final ResponseEntityProcessor<CommitStatuses> processor = new ResponseEntityProcessor<>(CommitStatuses.class);
    final int top = 25;
    int skip = 0;
    boolean shouldLoadMore;
    Collection<CommitStatus> result = new ArrayList<>();
    final int statusesThreshold = TeamCityProperties.getInteger(Constants.STATUSES_TO_LOAD_THRESHOLD_PROPERTY, Constants.STATUSES_TO_LOAD_THRESHOLD_DEFAULT_VAL);
    do {
      String url = String.format("%s&top=%d&skip=%d", baseUrl, top, skip);
      CommitStatuses commitStatuses = get(url, getCredentials(revision.getRoot(), myParams), null, processor);
      if (commitStatuses == null || commitStatuses.value == null || commitStatuses.value.isEmpty()) return result;
      result.addAll(commitStatuses.value);

      if (commitStatuses.value.stream().anyMatch(status -> targetBuildName.equals(status.context.name))) return result;
      skip += top;
      shouldLoadMore = result.size() < statusesThreshold;
    } while (shouldLoadMore);
    return result;
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable CommitStatus commitStatus) {
    if (commitStatus == null) return null;
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(getBuildName(buildPromotion), commitStatus.context.name);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType);
  }

  private Event getTriggeredEvent(CommitStatus commitStatus) {
    if (commitStatus.state == null) {
      LOG.warn("No Azure build status is provided. Related event can not be defined");
      return null;
    }
    StatusState status = StatusState.getByName(commitStatus.state);
    if (status == null) {
      LOG.warn(String.format("Undefined Azure build status: \"%s\". Related event can not be defined", commitStatus.state));
      return null;
    }
    switch (status) {
      case Pending:
        if (commitStatus.description == null) return null;
        return commitStatus.description.contains(DefaultStatusMessages.BUILD_QUEUED) ? Event.QUEUED :
               commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE) ? Event.REMOVED_FROM_QUEUE :
               commitStatus.description.contains(DefaultStatusMessages.BUILD_STARTED) ? Event.STARTED : null;
      case Error:
      case Succeeded:
      case Failed:
        return null;  // these statuses do not affect on further behaviour
      default:
        LOG.warn("No event is assosiated with Azure build status \"" + commitStatus.state + "\". Related event can not be defined");
    }
    return null;
  }

  public static void testConnection(@NotNull final VcsRoot root,
                                    @NotNull final Map<String, String> params,
                                    @NotNull final String commitId,
                                    @Nullable final KeyStore trustStore,
                                    @NotNull HttpCredentials credentials) throws PublisherException {
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      throw new PublisherException("Status publisher supports only Git VCS roots");
    }

    final TfsRepositoryInfo info = getServerAndProject(root, params);
    try {
      final String url = MessageFormat.format(COMMIT_STATUS_URL_FORMAT,
        info.getServer(), info.getProject(), info.getRepository(), commitId);
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.post(url, credentials, StringUtil.EMPTY, ContentType.DEFAULT_TEXT,
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
                                         @Nullable final KeyStore trustStore,
                                         @NotNull HttpCredentials credentials) throws PublisherException {
    final TfsRepositoryInfo info = getServerAndProject(root, params);
    try {
      List<Commit> latestCommits = getNLatestCommits(info, params, trustStore, 1, credentials);
      if (!latestCommits.isEmpty()) {
        return latestCommits.get(0).commitId;
      }
    } catch (Exception e) {
      final String message = FAILED_TO_TEST_CONNECTION_TO_REPOSITORY + info;
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }
    return null;
  }

  @NotNull
  private Set<String> getParentCommits( @NotNull final TfsRepositoryInfo info,
                                        @NotNull final String parentCommitId,
                                        @NotNull final Map<String, String> params,
                                        @Nullable final KeyStore trustStore,
                                        @NotNull VcsRoot root) throws PublisherException {
    final String url = MessageFormat.format(COMMIT_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), parentCommitId);
    final Set<String> commits = new HashSet<String>();

    try {
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.get(url, getCredentials(root, params),
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
      });
    } catch (Exception e) {
      final String message = "Azure DevOps publisher has failed to get parent commits in repository " + info;
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }

    return commits;
  }

  @Nullable
  private Iteration getPullRequestIteration(@NotNull final TfsRepositoryInfo info,
                                            @NotNull final String pullRequestId,
                                            @NotNull final Set<String> parentCommits,
                                            @NotNull final Map<String, String> params,
                                            @Nullable final KeyStore trustStore,
                                            @NotNull final VcsRoot root) throws PublisherException {
    final String url = MessageFormat.format(PULL_REQUEST_ITERATIONS_URL_FORMAT,
      info.getServer(), info.getProject(), info.getRepository(), pullRequestId);

    final AtomicReference<Iteration> iterationRef = new AtomicReference<>();

    try {
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.get(url, getCredentials(root, params),
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

              ListIterator<Iteration> iterationsIt = iterations.value.listIterator(iterations.value.size());
              Iteration likelyIteration = null;
              // iterate iterations from the last one, because it's the latest one
              while (iterationsIt.hasPrevious()) {
                Iteration it = iterationsIt.previous();
                if (it.sourceRefCommit == null || it.targetRefCommit == null || !parentCommits.contains(it.sourceRefCommit.commitId)) continue;

                String targetCommitId = it.targetRefCommit.commitId;
                if (parentCommits.contains(targetCommitId)) {
                  iterationRef.set(it);
                  return;
                }

                //get max iteration where sourceRefCommit is presented in parents if it is impossible to determine iteration by parents only
                if (likelyIteration == null)
                  likelyIteration = it;
              }

              if (likelyIteration != null) {
                LOG.debug("Matching iteration was not found among parents. Assuming most likely iteration by sourceRefCommit. " + info);
                iterationRef.set(likelyIteration);
              } else {
                LOG.debug("Iteration was not found " + info);
              }
            }
          });
      });
    } catch (Exception e) {
      final String message = String.format("Unable to get pull request %s iterations in repository %s", pullRequestId, info);
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }

    return iterationRef.get();
  }

  private static int numCommitsToLoad() {
    return TeamCityProperties.getInteger("teamcity.tfs.publisher.targetLookupSize", 128);
  }

  @NotNull
  private static List<Commit> getNLatestCommits(TfsRepositoryInfo info, Map<String, String> params, KeyStore trustStore, int nCommitsToLoad, @NotNull HttpCredentials credentials)
    throws HttpPublisherException, IOException {
    String moreCommitsUrl = MessageFormat.format(COMMITS_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), nCommitsToLoad);
    List<Commit> resultingCommits = new ArrayList<>();
    HttpHelper.get(moreCommitsUrl, credentials,
                   Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                   trustStore, new DefaultHttpResponseProcessor() {
        @Override
        public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
          super.processResponse(response);

          CommitsList commits = processGetResponse(response, CommitsList.class);
          if (commits == null || commits.value == null || commits.value.size() == 0) {
            throw new HttpPublisherException("No commits are available in repository %s" + info);
          }
          resultingCommits.addAll(commits.value);
        }
      });
    return resultingCommits;
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
      throw new HttpPublisherException("Azure DevOps publisher has received no response");
    }
    try {
      return myGson.fromJson(content, type);
    } catch (JsonSyntaxException e) {
      throw new HttpPublisherException("Invalid response while listing latest commits: " + e.getMessage(), e);
    }
  }

  private static void processErrorResponse(@NotNull final HttpHelper.HttpResponse response) throws HttpPublisherException {
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

  private boolean updateQueuedBuildStatus(@NotNull BuildPromotion buildPromotion,
                                          @NotNull BuildRevision revision,
                                          @NotNull AdditionalTaskInfo additionalTaskInfo,
                                          boolean removedFromQueue) throws PublisherException {
    final TfsRepositoryInfo info = getReposioryInfo(revision);
    if (info == null) {
      return false;
    }
    final CommitStatus status = getQueuedCommitStatus(buildPromotion, additionalTaskInfo, removedFromQueue);
    if (status.targetUrl == null) {
      LOG.debug(String.format("Can not build view URL for the build #%d. Probadly build configuration was removed. Status \"%s\" won't be published",
                              buildPromotion.getId(), status.state));
      return false;
    }
    if (status.state == null) {
      return false;
    }
    final String description = LogUtil.describe(buildPromotion);
    final String data = myGson.toJson(status);
    final String commitId = publishPullRequestStatus(info, revision, data, description);
    boolean published = publishCommitStatus(info, data, commitId, description, revision.getRoot());
    if (published) {
      myStatusesCache.removeStatusFromCache(revision, status.context.name);
    }
    return published;
  }

  private TfsRepositoryInfo getReposioryInfo(BuildRevision revision) throws PublisherException {
    final VcsRoot root = revision.getRoot();
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      LOG.warn("No revisions were found to update TFS Git commit status. Please check you have Git VCS roots in the build configuration");
      return null;
    }

    return getServerAndProject(root, myParams);
  }

  private boolean publishCommitStatus(TfsRepositoryInfo info, String data, String commitId, String description, VcsRoot root) throws PublisherException {
    final String commitStatusUrl = MessageFormat.format(COMMIT_STATUS_URL_FORMAT,
                                                        info.getServer(), info.getProject(), info.getRepository(), commitId);
    postJson(commitStatusUrl, getCredentials(root, myParams),
             data,
             Collections.singletonMap("Accept", "application/json"),
             description
    );
    return true;
  }

  @NotNull
  private String publishPullRequestStatus(@NotNull TfsRepositoryInfo info,
                                           @NotNull BuildRevision revision,
                                           @NotNull String data,
                                           @NotNull String description) throws PublisherException {
    // Check whether pull requests status publishing enabled
    String commitId = revision.getRevision();
    final String publishPullRequest = StringUtil.emptyIfNull(myParams.get(TfsConstants.PUBLISH_PULL_REQUESTS)).trim();
    if (!Boolean.parseBoolean(publishPullRequest)) {
      return commitId;
    }

    // Get branch and try to find pull request id
    final String branch = revision.getRepositoryVersion().getVcsBranch();
    if (StringUtil.isEmptyOrSpaces(branch)) {
      LOG.debug(String.format("Branch was not specified for commit %s, pull request status would not be published", commitId));
      return commitId;
    }

    final Matcher matcher = TFS_GIT_PULL_REQUEST_PATTERN.matcher(branch);
    if (!matcher.find()) {
      LOG.debug(String.format("Branch %s for commit %s does not contain info about pull request, status would not be published", branch, commitId));
      return commitId;
    }

    final String pullRequestId = matcher.group(1);

    final KeyStore trustStore = getSettings().trustStore();

    // Since it's a merge request we need to get parent commit for it
    final Set<String> commits = getParentCommits(info, commitId, myParams, trustStore, revision.getRoot());

    // Then we need to get pull request iteration where this commit present
    final Iteration iteration = getPullRequestIteration(info, pullRequestId, commits, myParams, trustStore, revision.getRoot());
    final String pullRequestStatusUrl;

    if (iteration == null || StringUtil.isEmptyOrSpaces(iteration.id)) {
      // Publish status for pull request
      pullRequestStatusUrl = MessageFormat.format(PULL_REQUEST_STATUS_URL_FORMAT,
                                                  info.getServer(), info.getProject(), info.getRepository(), pullRequestId);
    } else {
      if (iteration.sourceRefCommit != null && !StringUtil.isEmptyOrSpaces(iteration.sourceRefCommit.commitId))
        commitId = iteration.sourceRefCommit.commitId;
      // Publish status for pull request iteration
      pullRequestStatusUrl = MessageFormat.format(PULL_REQUEST_ITERATION_STATUS_URL_FORMAT,
                                                  info.getServer(), info.getProject(), info.getRepository(), pullRequestId, iteration.id);
    }

    postJson(pullRequestStatusUrl, getCredentials(revision.getRoot(), myParams),
             data,
             Collections.singletonMap("Accept", "application/json"),
             description
    );
    return commitId;
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final TfsRepositoryInfo info = getReposioryInfo(revision);
    if (info == null) {
      return;
    }
    final CommitStatus status = getCommitStatus(build, isStarting);
    final String description = LogUtil.describe(build);
    final String data = myGson.toJson(status);
    final String commitId = publishPullRequestStatus(info, revision, data, description);
    boolean published = publishCommitStatus(info, data, commitId, description, revision.getRoot());
    if (published) {
      myStatusesCache.removeStatusFromCache(revision, status.context.name);
    }
  }

  @NotNull
  private CommitStatus getCommitStatus(final SBuild build, final boolean isStarting) {
    StatusContext context = new StatusContext(getBuildName(build.getBuildPromotion()), "TeamCity");

    BuildPromotion buildPromotion = build.getBuildPromotion();
    boolean isCanceled = buildPromotion.isCanceled();
    StatusState state = getState(isStarting, isCanceled, build.getBuildStatus());
    String description = String.format("The build %s %s %s %s",
                                       build.getFullName(), build.getBuildNumber(),
                                       (isStarting || isCanceled) ? "is" : "has", isCanceled ? "canceled" : state.toString().toLowerCase());
    return new CommitStatus(state.getName(), description, getViewUrl(build), context);
  }

  private static StatusState getState(boolean isStarting, boolean isCanceled, Status status) {
    if (!isStarting) {
      if (status.isSuccessful()) return StatusState.Succeeded;
      else if (status == Status.ERROR) return StatusState.Error;
      else if (status == Status.FAILURE) return StatusState.Failed;
      else if (isCanceled) return StatusState.Error;
    }

    return StatusState.Pending;
  }

  @NotNull
  private CommitStatus getQueuedCommitStatus(@NotNull BuildPromotion buildPromotion, @NotNull AdditionalTaskInfo additionalTaskInfo, boolean removedFromQueue) {
    final StatusContext context = new StatusContext(getBuildName(buildPromotion), "TeamCity");

    String targetStatus = removedFromQueue ? StatusState.Failed.getName() : StatusState.Pending.getName();
    return new CommitStatus(targetStatus, additionalTaskInfo.getComment(), getViewUrl(buildPromotion), context);
  }

  @Nullable
  private static TfsRepositoryInfo tryGetServerAndProject(VcsRoot root, final Map<String, String> params) {
    final String url = root.getProperty(TfsConstants.GIT_VCS_URL);
    final String serverUrl = params.get(TfsConstants.SERVER_URL);
    return TfsRepositoryInfo.parse(url, serverUrl);
  }

  @NotNull
  private static TfsRepositoryInfo getServerAndProject(VcsRoot root, final Map<String, String> params) throws PublisherException {
    final TfsRepositoryInfo info = tryGetServerAndProject(root, params);
    if (info == null) {
      throw new PublisherException(String.format(
        "Invalid URL for TFS Git project '%s'. Publisher supports only TFS servers",
        root.getProperty(TfsConstants.GIT_VCS_URL)
      ));
    }

    return info;
  }

  @Nullable
  private HttpCredentials getCredentials(@NotNull VcsRoot root, Map<String, String> params) throws PublisherException {
    return getSettings().getCredentials(root, params);
  }

  private static class Error {
    private String message;
  }

  static class CommitStatus {
    final String state;
    final String description;
    final String targetUrl;
    final StatusContext context;

    public CommitStatus(String state, String description, String targetUrl, StatusContext context) {
      this.state = state;
      this.description = description;
      this.targetUrl = targetUrl;
      this.context = context;
    }
  }

  static class CommitStatuses {
    int count;
    Collection<CommitStatus> value;
  }

  static enum StatusState {
    @SerializedName("pending")
    Pending("pending"),

    @SerializedName("succeeded")
    Succeeded("succeeded"),

    @SerializedName("failed")
    Failed("failed"),

    @SerializedName("error")
    Error("error");

    private static final Map<String, StatusState> INDEX = Arrays.stream(values()).collect(Collectors.toMap(StatusState::getName, Function.identity()));

    private final String name;

    StatusState(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Nullable
    public static StatusState getByName(@NotNull String name) {
      return INDEX.get(name);
    }
  }

  static class StatusContext {

    public StatusContext(String name, String genre) {
      this.name = name;
      this.genre = genre;
    }

    String name;
    String genre;
  }

  private static class CommitsList {
    private List<Commit> value;
  }

  static class Commit {
    String commitId;
    List<String> parents;
    Author author;
  }

  static class Author {
    String name;
    Date date;
  }

  static class IterationsList {
    List<Iteration> value;
  }

  static class Iteration {
    String id;
    String createdDate;
    IterationCommit sourceRefCommit;
    IterationCommit targetRefCommit;
  }

  static class IterationCommit {
    String commitId;
  }
}
