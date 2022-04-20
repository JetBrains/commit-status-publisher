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
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;


/**
 * Updates TFS Git commit statuses via REST API.
 */
class TfsStatusPublisher extends HttpBasedCommitStatusPublisher {

  private static final String COMMITS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits?api-version=1.0&$top={3}";
  private static final String COMMIT_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits/{3}?api-version=1.0";
  private static final String COMMIT_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/commits/{3}/statuses?api-version=2.1";
  private static final String PULL_REQUEST_ITERATIONS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/iterations?api-version=3.0";
  private static final String PULL_REQUEST_ITERATION_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/iterations/{4}/statuses?api-version=3.0-preview";
  private static final String PULL_REQUEST_STATUS_URL_FORMAT = "{0}/{1}/_apis/git/repositories/{2}/pullRequests/{3}/statuses?api-version=3.0-preview";
  private static final String ERROR_AUTHORIZATION = "Check access token value and verify that it has Code (status) and Code (read) scopes";
  private static final String FAILED_TO_TEST_CONNECTION_TO_REPOSITORY = "TFS publisher has failed to test connection to repository ";
  private static final Gson myGson = new GsonBuilder()
                                          .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                                          .create();

  // Captures pull request identifier. Example: refs/pull/1/merge
  private static final Pattern TFS_GIT_PULL_REQUEST_PATTERN = Pattern.compile("^refs\\/pull\\/(\\d+)/merge");

  TfsStatusPublisher(@NotNull final CommitStatusPublisherSettings settings,
                     @NotNull final SBuildType buildType,
                     @NotNull final String buildFeatureId,
                     @NotNull final WebLinks webLinks,
                     @NotNull final Map<String, String> params,
                     @NotNull final CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems, webLinks);
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
    return updateBuildStatus(buildPromotion, revision, additionalTaskInfo);
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion,
                                       @NotNull BuildRevision revision,
                                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return updateBuildStatus(buildPromotion, revision, additionalTaskInfo);
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
  public void processResponse(@NotNull final HttpHelper.HttpResponse response) throws HttpPublisherException {
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
    boolean isSameBuild = StringUtil.areEqual(myLinks.getQueuedBuildUrl(removedBuild), commitStatus.targetUrl);
    return new RevisionStatus(event, commitStatus.description, isSameBuild);
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    CommitStatus commitStatus = getCommitStatus(revision, buildPromotion.getBuildType());
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  private CommitStatus getCommitStatus(BuildRevision revision, SBuildType buildType) throws PublisherException {
    final TfsRepositoryInfo info = getServerAndProject(revision.getRoot(), myParams);
    final String baseUrl = MessageFormat.format(COMMIT_STATUS_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), revision.getRevision());
    final String buildTypeExternalId = buildType.getExternalId();
    final ResponseEntityProcessor<CommitStatuses> processor = new ResponseEntityProcessor<>(CommitStatuses.class);
    final int top = 25;
    int skip = 0;

    CommitStatuses commitStatuses;
    do {
      String url = String.format("%s&top=%d&skip=%d", baseUrl, top, skip);
      commitStatuses = get(url, StringUtil.EMPTY, myParams.get(TfsConstants.ACCESS_TOKEN), null, processor);
      if (commitStatuses == null || commitStatuses.value == null || commitStatuses.value.isEmpty()) return null;
      Optional<CommitStatus> commitStatusOp = commitStatuses.value.stream()
                                                         .filter(status -> buildTypeExternalId.equals(status.context.name))
                                                         .findFirst();
      if (commitStatusOp.isPresent()) {
        return commitStatusOp.get();
      }
      skip += top;
    } while (commitStatuses.count >= top);
    return null;
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable CommitStatus commitStatus) {
    if (commitStatus == null) return null;
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(getViewUrl(buildPromotion), commitStatus.targetUrl);
    return new RevisionStatus(event, commitStatus.description, isSameBuild);
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
               commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE) ? Event.REMOVED_FROM_QUEUE : null;
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
                                    @Nullable final KeyStore trustStore) throws PublisherException {
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      throw new PublisherException("Status publisher supports only Git VCS roots");
    }

    final TfsRepositoryInfo info = getServerAndProject(root, params);
    try {
      final String url = MessageFormat.format(COMMIT_STATUS_URL_FORMAT,
        info.getServer(), info.getProject(), info.getRepository(), commitId);
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.post(url, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN), StringUtil.EMPTY, ContentType.DEFAULT_TEXT,
                        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                        trustStore, new DefaultHttpResponseProcessor() {
            @Override
            public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
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
                                         @Nullable final KeyStore trustStore) throws PublisherException {
    final TfsRepositoryInfo info = getServerAndProject(root, params);
    try {
      List<Commit> latestCommits = getNLatestCommits(info, params, trustStore, 1);
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
  private static Set<String> getParentCommits(@NotNull final TfsRepositoryInfo info,
                                              @NotNull final String parentCommitId,
                                              @NotNull final Map<String, String> params,
                                              @Nullable final KeyStore trustStore) throws PublisherException {
    final String url = MessageFormat.format(COMMIT_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), parentCommitId);
    final Set<String> commits = new HashSet<String>();

    try {
      IOGuard.allowNetworkCall(() -> {
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
                                                @NotNull final Set<String> parentCommits,
                                                @NotNull final Map<String, String> params,
                                                @Nullable final KeyStore trustStore) throws PublisherException {
    final String url = MessageFormat.format(PULL_REQUEST_ITERATIONS_URL_FORMAT,
      info.getServer(), info.getProject(), info.getRepository(), pullRequestId);

    final AtomicReference<String> iterationIdRef = new AtomicReference<>();

    try {
      IOGuard.allowNetworkCall(() -> {
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

              Map<String, Iteration> targetCommitIdForPossibleIterations = iterations.value.stream()
                                                    .filter(it -> null != it.sourceRefCommit && null != it.targetRefCommit
                                                                  && parentCommits.contains(it.sourceRefCommit.commitId))
                                                    .collect(Collectors.toMap(iteration -> iteration.targetRefCommit.commitId, Function.identity()));
              if (!targetCommitIdForPossibleIterations.isEmpty()) {
                for (Map.Entry<String, Iteration> targetCommitToIteration : targetCommitIdForPossibleIterations.entrySet()) {
                  String targetCommitId = targetCommitToIteration.getKey();
                  if (parentCommits.contains(targetCommitId)) {
                    iterationIdRef.set(targetCommitToIteration.getValue().id);
                    return;
                  }
                }
                LOG.debug("Matching iteration was not found among parents. Loading more commits from repository " + info);
                int commitsToLoad = numCommitsToLoad();
                Optional<Commit> commitFromRepo = getNLatestCommits(info, params, trustStore, commitsToLoad).stream()
                                                                .filter(commit -> targetCommitIdForPossibleIterations.containsKey(commit.commitId))
                                                                .max(Comparator.comparing(c -> c.author.date));
                if (commitFromRepo.isPresent()) {
                  String commitId = commitFromRepo.get().commitId;
                  Iteration iteration = targetCommitIdForPossibleIterations.get(commitId);
                  iterationIdRef.set(iteration.id);
                } else {
                  LOG.debug("Iteration was not found among " + commitsToLoad + " latest commits from repository " + info);
                }
              }
            }
          });
      });
    } catch (Exception e) {
      final String message = String.format("Unable to get pull request %s iterations in repository %s", pullRequestId, info);
      LOG.debug(message, e);
      throw new PublisherException(message, e);
    }

    return iterationIdRef.get();
  }

  private static int numCommitsToLoad() {
    return TeamCityProperties.getInteger("teamcity.tfs.publisher.targetLookupSize", 128);
  }

  @NotNull
  private static List<Commit> getNLatestCommits(TfsRepositoryInfo info, Map<String, String> params, KeyStore trustStore, int nCommitsToLoad)
    throws HttpPublisherException, IOException {
    String moreCommitsUrl = MessageFormat.format(COMMITS_URL_FORMAT, info.getServer(), info.getProject(), info.getRepository(), nCommitsToLoad);
    List<Commit> resultingCommits = new ArrayList<>();
    HttpHelper.get(moreCommitsUrl, StringUtil.EMPTY, params.get(TfsConstants.ACCESS_TOKEN),
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

  private static <T> T processGetResponse(@NotNull final HttpHelper.HttpResponse response, @NotNull final Class<T> type) throws HttpPublisherException {
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

  private boolean updateBuildStatus(@NotNull BuildPromotion buildPromotion,
                                    @NotNull BuildRevision revision,
                                    @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    final TfsRepositoryInfo info = getReposioryInfo(revision);
    if (info == null) {
      return false;
    }
    final CommitStatus status = getCommitStatus(buildPromotion, additionalTaskInfo);
    final String description = LogUtil.describe(buildPromotion);
    final String data = myGson.toJson(status);
    final String commitId = revision.getRevision();
    boolean isPublished = publishCommitStatus(info, data, commitId, description);
    if (!isPublished) {
      return false;
    }
    return publishPullRequestStatus(info, revision, data, commitId, description);
  }

  private TfsRepositoryInfo getReposioryInfo(BuildRevision revision) throws PublisherException {
    final VcsRoot root = revision.getRoot();
    if (!TfsConstants.GIT_VCS_ROOT.equals(root.getVcsName())) {
      LOG.warn("No revisions were found to update TFS Git commit status. Please check you have Git VCS roots in the build configuration");
      return null;
    }

    return getServerAndProject(root, myParams);
  }

  private boolean publishCommitStatus(TfsRepositoryInfo info, String data, String commitId, String description) {
    final String commitStatusUrl = MessageFormat.format(COMMIT_STATUS_URL_FORMAT,
                                                        info.getServer(), info.getProject(), info.getRepository(), commitId);
    postJson(commitStatusUrl, StringUtil.EMPTY, myParams.get(TfsConstants.ACCESS_TOKEN),
             data,
             Collections.singletonMap("Accept", "application/json"),
             description
    );
    return true;
  }

  private boolean publishPullRequestStatus(@NotNull TfsRepositoryInfo info,
                                           @NotNull BuildRevision revision,
                                           @NotNull String data,
                                           @NotNull String commitId,
                                           @NotNull String description) throws PublisherException {
    // Check whether pull requests status publishing enabled
    final String publishPullRequest = StringUtil.emptyIfNull(myParams.get(TfsConstants.PUBLISH_PULL_REQUESTS)).trim();
    if (!Boolean.parseBoolean(publishPullRequest)) {
      return true;
    }

    // Get branch and try to find pull request id
    final String branch = revision.getRepositoryVersion().getVcsBranch();
    if (StringUtil.isEmptyOrSpaces(branch)) {
      LOG.debug(String.format("Branch was not specified for commit %s, pull request status would not be published", commitId));
      return true;
    }

    final Matcher matcher = TFS_GIT_PULL_REQUEST_PATTERN.matcher(branch);
    if (!matcher.find()) {
      LOG.debug(String.format("Branch %s for commit %s does not contain info about pull request, status would not be published", branch, commitId));
      return true;
    }

    final String pullRequestId = matcher.group(1);

    final KeyStore trustStore = getSettings().trustStore();

    // Since it's a merge request we need to get parent commit for it
    final Set<String> commits = getParentCommits(info, commitId, myParams, trustStore);

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

    postJson(pullRequestStatusUrl, StringUtil.EMPTY, myParams.get(TfsConstants.ACCESS_TOKEN),
             data,
             Collections.singletonMap("Accept", "application/json"),
             description
    );
    return true;
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final TfsRepositoryInfo info = getReposioryInfo(revision);
    if (info == null) {
      return;
    }
    final CommitStatus status = getCommitStatus(build, isStarting);
    final String description = LogUtil.describe(build);
    final String data = myGson.toJson(status);
    final String commitId = revision.getRevision();

    boolean isPublished = publishCommitStatus(info, data, commitId, description);
    if (!isPublished) {
      return;
    }
    publishPullRequestStatus(info, revision, data, commitId, description);
  }

  @NotNull
  private CommitStatus getCommitStatus(final SBuild build, final boolean isStarting) {
    StatusContext context = new StatusContext();
    context.name = build.getBuildTypeExternalId();
    context.genre = "TeamCity";

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
  private CommitStatus getCommitStatus(@NotNull BuildPromotion buildPromotion, @NotNull AdditionalTaskInfo additionalTaskInfo) {
    final StatusContext context = new StatusContext();
    context.name = buildPromotion.getBuildTypeExternalId();
    context.genre = "TeamCity";

    String targetStatus = (additionalTaskInfo.isPromotionReplaced() || !buildPromotion.isCanceled()) ?
                          StatusState.Pending.getName() : StatusState.Error.getName();
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

  private static class Error {
    private String message;
  }

  static class CommitStatus {
    private final String state;
    private final String description;
    private final String targetUrl;
    private final StatusContext context;

    public CommitStatus(String state, String description, String targetUrl, StatusContext context) {
      this.state = state;
      this.description = description;
      this.targetUrl = targetUrl;
      this.context = context;
    }
  }

  private static class CommitStatuses {
    private int count;
    private List<CommitStatus> value;
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
    private Author author;
  }

  private static class Author {
    private String name;
    private Date date;
  }

  private static class IterationsList {
    private List<Iteration> value;
  }

  private static class Iteration {
    private String id;
    private IterationCommit sourceRefCommit;
    private IterationCommit targetRefCommit;
  }

  private static class IterationCommit {
    private String commitId;
  }
}
