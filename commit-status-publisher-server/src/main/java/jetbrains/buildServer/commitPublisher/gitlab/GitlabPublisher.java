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

package jetbrains.buildServer.commitPublisher.gitlab;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitlab.data.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GitlabPublisher extends HttpBasedCommitStatusPublisher<GitlabBuildStatus> {

  private static final String REFS_HEADS = "refs/heads/";
  private static final String REFS_TAGS = "refs/tags/";
  private static final String MERGE_REQUEST_GROUP_NO = "mrNo";
  private static final Pattern PATTERN_REF_MERGE_RESULT = Pattern.compile("^refs/merge-requests/(?<" + MERGE_REQUEST_GROUP_NO + ">\\d+)/merge$");
  private static final String REF_TYPE_BRANCH = "branch";
  private static final Gson myGson = new Gson();
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  @NotNull private final CommitStatusesCache<GitLabReceiveCommitStatus> myStatusesCache;
  @NotNull private final VcsModificationHistoryEx myVcsModificationHistory;

  GitlabPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType,
                  @NotNull String buildFeatureId,
                  @NotNull WebLinks links,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems,
                  @NotNull CommitStatusesCache<GitLabReceiveCommitStatus> statusesCache,
                  @NotNull VcsModificationHistoryEx vcsModificationHistory) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    myStatusesCache = statusesCache;
    myVcsModificationHistory = vcsModificationHistory;
  }


  @NotNull
  @Override
  public String getId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }


  @NotNull
  @Override
  public String toString() {
    return "GitLab";
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    publish(buildPromotion, revision, GitlabBuildStatus.PENDING, additionalTaskInfo);
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    if (!additionalTaskInfo.isBuildManuallyRemovedOrCanceled()) return false;
    publish(buildPromotion, revision, GitlabBuildStatus.CANCELED , additionalTaskInfo);
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GitlabBuildStatus.RUNNING, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }


  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    GitlabBuildStatus status = build.getBuildStatus().isSuccessful() ? GitlabBuildStatus.SUCCESS : GitlabBuildStatus.FAILED;
    publish(build, revision, status, build.getStatusDescriptor().getText());
    return true;
  }


  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GitlabBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build, revision, buildInProgress ? GitlabBuildStatus.RUNNING : GitlabBuildStatus.SUCCESS, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    return true;
  }


  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, GitlabBuildStatus.CANCELED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    SBuildType buildType = removedBuild.getBuildType();
    GitLabReceiveCommitStatus commitStatus = getLatestCommitStatusForBuild(revision, buildType.getFullName(), removedBuild.getBuildPromotion());
    return getRevisionStatusForRemovedBuild(removedBuild, commitStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable GitLabReceiveCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(getBuildName(removedBuild.getBuildPromotion()), commitStatus.name);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType);
  }

  private String getBuildName(BuildPromotion promotion) {
    SBuildType buildType = promotion.getBuildType();
    return buildType != null ? buildType.getFullName() : promotion.getBuildTypeExternalId();
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    SBuildType buildType = buildPromotion.getBuildType();
    GitLabReceiveCommitStatus commitStatus = getLatestCommitStatusForBuild(revision, buildType == null ? buildPromotion.getBuildTypeExternalId() : buildType.getFullName(), buildPromotion);
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  private GitLabReceiveCommitStatus getLatestCommitStatusForBuild(@NotNull BuildRevision revision, @NotNull String buildName, @NotNull BuildPromotion promotion) throws PublisherException {
    AtomicReference<PublisherException> exception = new AtomicReference<>(null);
    GitLabReceiveCommitStatus statusFromCache = myStatusesCache.getStatusFromCache(revision, buildName, () -> {
      SBuildType exactBuildTypeToLoadStatuses = promotion.isPartOfBuildChain() ? null : promotion.getBuildType();
      try {
        GitLabReceiveCommitStatus[] commitStatuses = loadGitLabStatuses(revision, exactBuildTypeToLoadStatuses);
        return Arrays.asList(commitStatuses);
      } catch (PublisherException e) {
        exception.set(e);
        return Collections.emptyList();
      }
    }, status -> status.name);

    if (exception.get() != null) {
      throw exception.get();
    }

    return statusFromCache;
  }

  private GitLabReceiveCommitStatus[] loadGitLabStatuses(@NotNull BuildRevision revision, @Nullable SBuildType buildType) throws PublisherException {
    String url = buildRevisionStatusesUrl(revision, buildType);
    final HttpCredentials credentials = getSettings().getCredentials(revision.getRoot(), myParams);
    ResponseEntityProcessor<GitLabReceiveCommitStatus[]> processor = new ResponseEntityProcessor<>(GitLabReceiveCommitStatus[].class);
    GitLabReceiveCommitStatus[] commitStatuses = get(url, credentials, null, processor);
    if (commitStatuses == null || commitStatuses.length == 0) {
      return new GitLabReceiveCommitStatus[0];
    }
    return commitStatuses;
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable GitLabReceiveCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(getBuildName(buildPromotion), commitStatus.name);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType);
  }

  private String buildRevisionStatusesUrl(@NotNull BuildRevision revision, @Nullable BuildType buildType) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl(root.getProperty("url"));
    String pathPrefix = GitlabSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String statusesUrl = GitlabSettings.getProjectsUrl(apiUrl, repository.owner(), repository.repositoryName()) + "/repository/commits/" + revision.getRevision() + "/statuses";
    if (buildType != null) {
      statusesUrl += ("?" + encodeParameter("name", buildType.getFullName()));
    }
    return statusesUrl;
  }

  private Event getTriggeredEvent(GitLabReceiveCommitStatus commitStatus) {
    if (commitStatus.status == null) {
      LOG.warn("No GitLab build status is provided. Related event can not be calculated");
      return null;
    }
    GitlabBuildStatus status = GitlabBuildStatus.getByName(commitStatus.status);
    if (status == null) {
      LOG.warn("Unknown GitLab build status \"" + commitStatus.status + "\". Related event can not be calculated");
      return null;
    }

    switch (status) {
      case CANCELED:
        return (commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)) ? Event.REMOVED_FROM_QUEUE : null;
      case PENDING:
        return Event.QUEUED;
      case RUNNING:
        return (commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_STARTED))? Event.STARTED : null;
      case SUCCESS:
      case FAILED:
        return null;
      default:
        LOG.warn("No event is assosiated with GitLab build status \"" + status + "\". Related event can not be defined");
    }
    return null;
  }


  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull GitlabBuildStatus status,
                       @NotNull String description) throws PublisherException {
    String buildName = getBuildName(build.getBuildPromotion());
    String message = createMessage(status, buildName, revision, getViewUrl(build), description);
    publish(message, revision, LogUtil.describe(build));
    myStatusesCache.removeStatusFromCache(revision, buildName);
  }

  private void publish(@NotNull BuildPromotion buildPromotion,
                       @NotNull BuildRevision revision,
                       @NotNull GitlabBuildStatus status,
                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String url = getViewUrl(buildPromotion);
    if (url == null) {
      LOG.debug(String.format("Can not build view URL for the build #%d. Probadly build configuration was removed. Status \"%s\" won't be published",
                              buildPromotion.getId(), status.getName()));
      return;
    }
    String description = additionalTaskInfo.getComment();
    String buildName = getBuildName(buildPromotion);
    String message = createMessage(status, buildName, revision, url, description);
    publish(message, revision, LogUtil.describe(buildPromotion));
    myStatusesCache.removeStatusFromCache(revision, buildName);
  }

  private void publish(@NotNull String message,
                       @NotNull BuildRevision revision,
                       @NotNull String buildDescription) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl(root.getProperty("url"));
    String pathPrefix = GitlabSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());

    final HttpCredentials credentials = getSettings().getCredentials(root, myParams);
    try {
      final String commit = determineStatusCommit(credentials, repository, revision);
      if (commit != null) {
        publish(credentials, commit, message, repository, buildDescription);
      }
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to GitLab for VCS root " +
                                   revision.getRoot().getName() + ": " + e, e);
    }
  }

  private void publish(@Nullable HttpCredentials credentials,
                       @NotNull String commit,
                       @NotNull String data,
                       @NotNull Repository repository,
                       @NotNull String buildDescription) throws PublisherException {
    String url = GitlabSettings.getProjectsUrl(getApiUrl(repository.url()), repository.owner(), repository.repositoryName()) + "/statuses/" + commit;
    LOG.debug("Request url: " + url + ", message: " + data);
    postJson(url, credentials, data, null, buildDescription);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400) {
      String responseString = response.getContent();
      if (404 == statusCode) {
        throw new HttpPublisherException(statusCode, "Repository not found. Please check if it was renamed or moved to another namespace");
      } else if (!responseString.contains("Cannot transition status via :enqueue from :pending") &&
          !responseString.contains("Cannot transition status via :enqueue from :running") &&
          !responseString.contains("Cannot transition status via :run from :running")) {
        throw new HttpPublisherException(statusCode,
          response.getStatusText(), "HTTP response error: " + responseString);
      }
    }
  }

  @NotNull
  private String createMessage(@NotNull GitlabBuildStatus status,
                               @NotNull String name,
                               @NotNull BuildRevision revision,
                               @NotNull String url,
                               @NotNull String description) {

    RepositoryVersion repositoryVersion = revision.getRepositoryVersion();
    String ref = repositoryVersion.getVcsBranch();
    if (ref != null) {
      if (ref.startsWith(REFS_HEADS)) {
        ref = ref.substring(REFS_HEADS.length());
      } else if (ref.startsWith(REFS_TAGS)) {
        ref = ref.substring(REFS_TAGS.length());
      } else {
        ref = null;
      }
    }

    GitLabPublishCommitStatus data = new GitLabPublishCommitStatus(status.getName(), description, name, url, ref);
    return myGson.toJson(data);
  }

  @Nullable
  static Repository parseRepository(@NotNull VcsRoot root, @Nullable String pathPrefix) {
    if ("jetbrains.git".equals(root.getVcsName())) {
      String url = root.getProperty("url");
      return url == null ? null : VCS_URL_PARSER.parseRepositoryUrl(url, pathPrefix);
    } else {
      return null;
    }
  }


  @NotNull
  private String getApiUrl(@Nullable String vcsRootUrl) throws PublisherException {
    if (!StringUtil.isEmptyOrSpaces(myParams.get(Constants.GITLAB_API_URL)))
      return HttpHelper.stripTrailingSlash(myParams.get(Constants.GITLAB_API_URL));

    String url = vcsRootUrl;
    if (url == null) {
      List<SVcsRoot> roots = myBuildType.getVcsRoots();
      if (roots.size() == 0) throw new PublisherException("Could not find VCS Root to extract URL");

      url = roots.get(0).getProperty("url");
      if (StringUtil.isEmptyOrSpaces(url)) throw new PublisherException("Could not find VCS Root URL to transform it into GitLab API URL");
    }

    String apiUrl = GitlabSettings.guessGitLabApiURL(url);
    if (StringUtil.isEmptyOrSpaces(apiUrl)) throw new PublisherException("Could not transform VCS Root URL into GitLab API URL");

    return apiUrl;
  }

  /**
   * Determines the commit to publish the status to.
   * By default, this is the build revision's revision.
   * For merge result commits (<code>refs/merge-requests/x/merge</code>) this is the parent that belongs to the merge request's source branch.
   * @param credentials HTTP credentials for GitLab Rest API
   * @param repository repository coordinates
   * @param buildRevision the build revision
   * @return commit SHA or null if determining commit was not possible
   */
  @Nullable
  private String determineStatusCommit(@Nullable HttpCredentials credentials,
                                       @NotNull Repository repository,
                                       @NotNull BuildRevision buildRevision) throws PublisherException {
    final String revision = buildRevision.getRevision();

    if (!supportMergeResults(myBuildType)) {
      return revision;
    }

    final String vcsBranch = buildRevision.getRepositoryVersion().getVcsBranch();
    if (vcsBranch == null) {
      return revision;
    }

    final Matcher matcher = PATTERN_REF_MERGE_RESULT.matcher(vcsBranch);
    if (!matcher.matches()) {
      return revision;
    }

    final String mergeRequestNumber = matcher.group(MERGE_REQUEST_GROUP_NO);
    final GitLabMergeRequest mergeRequest = getMergeRequest(credentials, repository, mergeRequestNumber);
    if (mergeRequest == null) {
      return null;
    }

    final Set<String> parentRevisions = getParentRevisions(buildRevision.getRoot(), revision);
    return determineParentInSourceBranch(credentials, repository, mergeRequest, parentRevisions);
  }

  @Nullable
  private GitLabMergeRequest getMergeRequest(@Nullable HttpCredentials credentials, @NotNull Repository repository, @NotNull String mergeRequestNumber) throws PublisherException {
    final String url = GitlabSettings.getProjectsUrl(getApiUrl(repository.url()), repository.owner(), repository.repositoryName()) + "/merge_requests/" + mergeRequestNumber;
    final ResponseEntityProcessor<GitLabMergeRequest> processor = new ResponseEntityProcessor<>(GitLabMergeRequest.class);
    final GitLabMergeRequest mergeRequest = get(url, credentials, null, processor);
    if (mergeRequest == null) {
      LOG.warn("unable to retrieve Gitlab merge request " + mergeRequestNumber);
    }
    return mergeRequest;
  }

  @NotNull
  private Set<String> getParentRevisions(@NotNull VcsRootInstance root, @NotNull String revision) {
    final SVcsModification modification = myVcsModificationHistory.findModificationByVersion(root, revision);
    if (modification == null) {
      LOG.warn("unable to find GitLab merge result revision " + revision + " in VCS root " + root + ", status publishing will be skipped");
      return Collections.emptySet();
    }
    final Collection<String> parentRevisions = modification.getParentRevisions();
    if (parentRevisions.isEmpty()) {
      LOG.warn("no parent revisions found for revision " + revision + " in VCS root " + root + ", status publishing will be skipped");
    }
    return ImmutableSet.copyOf(parentRevisions);
  }

  @Nullable
  private String determineParentInSourceBranch(@Nullable HttpCredentials credentials,
                                               @NotNull Repository repository,
                                               @NotNull GitLabMergeRequest mergeRequest,
                                               @NotNull Set<String> parentRevisions) throws PublisherException {
    final String headSha = mergeRequest.sha;
    if (headSha != null && parentRevisions.contains(headSha)) {
      return headSha;
    }

    for (String parentRevision : parentRevisions) {
      if (isOnlyInSourceBranch(credentials, repository, mergeRequest, parentRevision)) {
        return parentRevision;
      }
    }

    return null;
  }

  private boolean isOnlyInSourceBranch(HttpCredentials credentials,
                                       @NotNull Repository repository,
                                       @NotNull GitLabMergeRequest mergeRequest,
                                       @NotNull String revision) throws PublisherException {
    final String url = GitlabSettings.getProjectsUrl(getApiUrl(repository.url()), repository.owner(), repository.repositoryName()) + "/repository/commits/" + revision + "/refs?type=branch";
    final ResponseEntityProcessor<GitLabCommitReference[]> processor = new ResponseEntityProcessor<>(GitLabCommitReference[].class);
    final GitLabCommitReference[] references = get(url, credentials, null, processor);
    if (references == null) {
      return false;
    }

    boolean inSource = false;
    boolean inTarget = false;
    for (GitLabCommitReference reference : references) {
      if (REF_TYPE_BRANCH.equals(reference.type)) {
        if (mergeRequest.source_branch.equals(reference.name)) {
          inSource = true;
        }
        if (mergeRequest.target_branch.equals(reference.name)) {
          inTarget = true;
        }
      }
    }
    return inSource && !inTarget;
  }

  private static boolean supportMergeResults(@NotNull BuildType buildType) {
    if (buildType instanceof InternalParameters) {
      return ((InternalParameters)buildType).getBooleanInternalParameterOrTrue(Constants.GITLAB_FEATURE_TOGGLE_MERGE_RESULTS);
    }

    return true;
  }
}
