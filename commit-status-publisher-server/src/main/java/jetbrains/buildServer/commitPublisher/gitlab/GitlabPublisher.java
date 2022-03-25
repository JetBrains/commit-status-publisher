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

import com.google.gson.Gson;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabCommitStatus;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabPublishCommitStatus;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabReceiveCommitStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GitlabPublisher extends HttpBasedCommitStatusPublisher {

  private static final String REFS_HEADS = "refs/heads/";
  private static final String REFS_TAGS = "refs/tags/";
  private final Gson myGson = new Gson();
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();
  private final CustomDataStorageManager myDataStorageManager;

  GitlabPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                  @NotNull WebLinks links,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems,
                  @NotNull CustomDataStorageManager dataStorageManager) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    myDataStorageManager = dataStorageManager;
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
    GitlabBuildStatus targetStatus = additionalTaskInfo.isPromotionReplaced() ? GitlabBuildStatus.PENDING : GitlabBuildStatus.CANCELED;
    publish(buildPromotion, revision, targetStatus, additionalTaskInfo);
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
    GitLabReceiveCommitStatus commitStatus = getLatestCommitStatusForBuildFromStorage(removedBuild.getBuildType(), revision);
    if (commitStatus == null) {
      commitStatus = getLatestCommitStatusForBuildFromVcs(revision, removedBuild.getBuildType());
    }
    return getRevisionStatusForRemovedBuild(removedBuild, commitStatus);
  }

  @Nullable
  private GitLabReceiveCommitStatus getLatestCommitStatusForBuildFromStorage(@NotNull SBuildType buildType, @NotNull BuildRevision revision) {
    CustomDataStorage statusHistoryDataStorage = getStatusHistoryDataStorage();
    Map<String, String> statuses = statusHistoryDataStorage.getValues();
    if (statuses != null) {
      final String keyPrefix = getStorageKeyPrefix(buildType.getName(), revision.getRevision());
      Optional<String> latestSatusOpt = statuses.entrySet().stream()
                                                .filter(entry -> entry.getKey().startsWith(keyPrefix))
                                                .sorted(Comparator.comparing(this::getDateFromStorageKey).reversed())
                                                .map(Map.Entry::getValue)
                                                .findFirst();
      if (latestSatusOpt.isPresent()) {
        GitLabPublishCommitStatus latestStatus = myGson.fromJson(latestSatusOpt.get(), GitLabPublishCommitStatus.class);
        return convertToReceiveStatus(latestStatus);
      }
    }
    return null;
  }

  private GitLabReceiveCommitStatus convertToReceiveStatus(GitLabPublishCommitStatus status) {
    return new GitLabReceiveCommitStatus(null, status.state, status.description, status.name, status.target_url);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @Nullable GitLabReceiveCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(myLinks.getQueuedBuildUrl(removedBuild), commitStatus.target_url);
    return new RevisionStatus(event, isSameBuild);
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    GitLabReceiveCommitStatus commitStatus = buildPromotion.getBuildType() != null ? getLatestCommitStatusForBuildFromStorage(buildPromotion.getBuildType(), revision) : null;
    if (commitStatus == null) {
      commitStatus = getLatestCommitStatusForBuildFromVcs(revision, buildPromotion.getBuildType());
    }
    return getRevisionStatus(buildPromotion, commitStatus);
  }

  @Override
  public boolean publish(BuildRevision revision, CommonBuildStatus status) throws PublisherException {
    if (status == null) return false;
    GitlabBuildStatus buildStatus = GitlabBuildStatus.getByName(status.getState());
    if (buildStatus == null) {
      return false;
    }
    String buildName = status.getBuild();
    String message = createMessage(buildStatus, buildName, revision, status.getUrl(), status.getDescription());
    publish(message, revision, status.toString());
    storeStatusInHistory(buildName, revision.getRevision(), message);
    return true;
  }

  @Override
  public CommonBuildStatus getLatestInformativeBuildStatusForPromotion(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) {
    Optional<GitLabPublishCommitStatus> statusOpt = getBuildStatusFromStorage(buildPromotion.getBuildType().getName(), revision,
                                                                              str -> myGson.fromJson(str, GitLabPublishCommitStatus.class),
                                                                              new GitLabInformativeCommitStatusFilter<GitLabPublishCommitStatus>(buildPromotion));
    if (statusOpt.isPresent()) {
      GitLabPublishCommitStatus status = statusOpt.get();
      return new CommonBuildStatus(status.name, status.state, status.description, status.target_url);
    }
    return null;
  }

  private GitLabReceiveCommitStatus getLatestCommitStatusForBuildFromVcs(@NotNull BuildRevision revision, @Nullable SBuildType buildType) throws PublisherException {
    String url = buildRevisionStatusesUrl(revision, buildType, 1, 1,false);
    ResponseEntityProcessor<GitLabReceiveCommitStatus[]> processor = new ResponseEntityProcessor<>(GitLabReceiveCommitStatus[].class);
    GitLabReceiveCommitStatus[] commitStatuses = get(url, null, null, Collections.singletonMap("PRIVATE-TOKEN", getPrivateToken()), processor);
    if (commitStatuses == null || commitStatuses.length == 0) {
      return null;
    }
    return commitStatuses[0];
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable GitLabReceiveCommitStatus commitStatus) {
    if(commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(getViewUrl(buildPromotion), commitStatus.target_url);
    return new RevisionStatus(event, isSameBuild);
  }

  private String buildRevisionStatusesUrl(@NotNull BuildRevision revision, @Nullable BuildType buildType, @Nullable Integer page,
                                          @Nullable Integer perPage, @Nullable Boolean filterSha) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    final String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing GitLab API URL parameter");
    String pathPrefix = GitlabSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    StringBuilder statusesUrl = new StringBuilder(GitlabSettings.getProjectsUrl(apiUrl, repository.owner(), repository.repositoryName()))
      .append("/repository/commits/")
      .append(revision.getRevision())
      .append("/statuses");
    boolean isParameterAppended = false;
    if (buildType != null) {
      statusesUrl.append("?").append(encodeParameter("name", buildType.getName()));
      isParameterAppended = true;
    }
    if (page != null) {
      statusesUrl.append(isParameterAppended ? '&' : '?');
      statusesUrl.append(encodeParameter("page", page.toString()));
      isParameterAppended = true;
    }
    if (perPage != null) {
      statusesUrl.append(isParameterAppended ? '&' : '?');
      statusesUrl.append(encodeParameter("per_page", perPage.toString()));
      isParameterAppended = true;
    }
    if (Boolean.TRUE.equals(filterSha)) {
      statusesUrl.append(isParameterAppended ? '&' : '?');
      statusesUrl.append(encodeParameter("sha", revision.getRevision()));
    }
    return statusesUrl.toString();
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
        if (commitStatus.description != null && commitStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)) {
          return Event.REMOVED_FROM_QUEUE;
        }
        return null;
      case PENDING:
        return Event.QUEUED;
      case RUNNING:
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
    String buildName = build.getBuildTypeName();
    String message = createMessage(status, buildName, revision, myLinks.getViewResultsUrl(build), description);
    publish(message, revision, LogUtil.describe(build));
    storeStatusInHistory(buildName, revision.getRevision(), message);
  }

  private void publish(@NotNull BuildPromotion buildPromotion,
                       @NotNull BuildRevision revision,
                       @NotNull GitlabBuildStatus status,
                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String url = getViewUrl(buildPromotion);
    String description = additionalTaskInfo.compileQueueRelatedMessage();
    String buildName = buildPromotion.getBuildType().getName();
    String message = createMessage(status, buildName, revision, url, description);
    publish(message, revision, LogUtil.describe(buildPromotion));
    storeStatusInHistory(buildName, revision.getRevision(), message);
  }

  private void publish(@NotNull String message,
                       @NotNull BuildRevision revision,
                       @NotNull String buildDescription) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    String apiUrl = getApiUrl();
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing GitLab API URL parameter");
    String pathPrefix = GitlabSettings.getPathPrefix(apiUrl);
    Repository repository = parseRepository(root, pathPrefix);
    if (repository == null)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());

    try {
      publish(revision.getRevision(), message, repository, buildDescription);
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to GitLab for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  private void publish(@NotNull String commit, @NotNull String data, @NotNull Repository repository, @NotNull String buildDescription) {
    String url = GitlabSettings.getProjectsUrl(getApiUrl(), repository.owner(), repository.repositoryName()) + "/statuses/" + commit;
    LOG.debug("Request url: " + url + ", message: " + data);
    postJson(url, null, null, data, Collections.singletonMap("PRIVATE-TOKEN", getPrivateToken()), buildDescription);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400) {
      String responseString = response.getContent();
      if (!responseString.contains("Cannot transition status via :enqueue from :pending") &&
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


  private String getApiUrl() {
    return HttpHelper.stripTrailingSlash(myParams.get(Constants.GITLAB_API_URL));
  }

  private String getPrivateToken() {
    return myParams.get(Constants.GITLAB_TOKEN);
  }

  protected CustomDataStorage getStatusHistoryDataStorage() {
    return myDataStorageManager.getCustomDataStorage(getClass());
  }

  private class GitLabInformativeCommitStatusFilter<T extends GitLabCommitStatus> extends InformativeCommitStatusFilter<T> {
    public GitLabInformativeCommitStatusFilter(BuildPromotion buildPromotion) {
      super(getPossibleViewUrls(buildPromotion));
    }

    @Override
    protected String getUrl(T status) {
      return status.target_url;
    }

    @Override
    protected String getDescription(T status) {
      return status.description;
    }
  }
}
