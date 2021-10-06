/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.stash;

import com.google.gson.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.stash.data.JsonStashBuildStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.VersionComparatorUtil;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class StashPublisher extends HttpBasedCommitStatusPublisher {
  public static final String PROP_PUBLISH_QUEUED_BUILD_STATUS = "teamcity.stashCommitStatusPublisher.publishQueuedBuildStatus";
  private static final Pattern PULL_REQUEST_BRANCH_PATTERN = Pattern.compile("^refs\\/pull\\-requests\\/\\d+\\/from");

  private final Gson myGson = new Gson();
  private final WebLinks myLinks;
  private BitbucketEndpoint myBitbucketEndpoint = null;

  StashPublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull WebLinks links, @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
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
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) {
    vote(buildPromotion, revision, StashBuildStatus.INPROGRESS, "Build queued");
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
    // commented because it is triggered just before starting build. It's not clear for now is such behaviour is normal
    /* StringBuilder description = new StringBuilder("Build removed from queue");
    if (user != null)
      description.append(" by ").append(user.getName());
    if (comment != null)
      description.append(" with comment \"").append(comment).append("\"");
    vote(buildPromotion, revision, StashBuildStatus.FAILED, description.toString());
    return true; */
    return false;
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
  private boolean useBuildAPI() {
    if (myBuildType instanceof BuildTypeEx && ((BuildTypeEx)myBuildType).getBooleanInternalParameter("commitStatusPublisher.enforceDeprecatedAPI"))
      return true;
    // NOTE: compare(null, "7.4") < 0
    return VersionComparatorUtil.compare(getSettings().getServerVersion(getBaseUrl()), "7.4") < 0;
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    String vcsBranch = getVcsBranch(revision, LogUtil.describe(build));
    SBuildData data = new SBuildData(build, revision, status, comment, vcsBranch);
    getEndpoint().publishBuildStatus(data, LogUtil.describe(build));
  }

  private void vote(@NotNull BuildPromotion buildPromotion,
                    @NotNull BuildRevision revision,
                    @NotNull StashBuildStatus status,
                    @NotNull String comment) {
    String vcsBranch = getVcsBranch(revision, LogUtil.describe(buildPromotion));
    SBuildPromotionData data = new SBuildPromotionData(buildPromotion, revision, status, comment, vcsBranch);
    getEndpoint().publishBuildStatus(data, LogUtil.describe(buildPromotion));
  }

  @Nullable
  private String getVcsBranch(@NotNull BuildRevision revision, @NotNull String buildDescription) {
    String revisionVcsBranch = revision.getRepositoryVersion().getVcsBranch();
    if (revisionVcsBranch == null || !PULL_REQUEST_BRANCH_PATTERN.matcher(revisionVcsBranch).matches()) {
      return revisionVcsBranch;
    }
    PullRequestsList pullRequestsList = getEndpoint().getPullRequests(revision, buildDescription);
    if (pullRequestsList == null) {
      return revisionVcsBranch;
    }

    try {
      String sourceCommitId = revision.getRepositoryVersion().getVersion();
      String targetCommitId = revision.getRoot().getCurrentRevision().getVersion();
      for (PullRequest pullRequest : pullRequestsList.values) {
        if (sourceCommitId.equals(pullRequest.fromRef.latestCommit) && targetCommitId.equals(pullRequest.toRef.latestCommit)) {
          return pullRequest.fromRef.id;
        }
      }
      LOG.debug("Can not find pull request for source branch with commit id '" + sourceCommitId + "' and target branch with commit id '" + targetCommitId + "'");
    } catch (VcsException e) {
      return revisionVcsBranch;
    }
    return revisionVcsBranch;
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

  private BitbucketEndpoint getEndpoint() {
    if (myBitbucketEndpoint == null)
      myBitbucketEndpoint = useBuildAPI() ? new BuildApiEndpoint() : new CoreApiEndpoint();
    return myBitbucketEndpoint;
  }

  private interface StatusData {
    @NotNull String getCommit();
    @NotNull String getState();
    @NotNull String getKey();
    @NotNull String getName();
    @NotNull String getUrl();
    @NotNull String getDescription();
    @NotNull String getBuildNumber();
    @Nullable VcsRootInstance getVcsRootInstance();
    long getBuildDurationMs();
    @Nullable String getVcsBranch();

    BuildStatistics getBuildStatistics();
  }

  private abstract class BaseBuildData implements StatusData {
    private final BuildRevision myRevision;
    private final StashBuildStatus myStatus;
    private final String myDescription;
    private final String myVcsBranch;

    BaseBuildData(@NotNull BuildRevision revision, @NotNull StashBuildStatus status, @NotNull String description, @Nullable String vcsBranch) {
      myRevision = revision;
      myStatus = status;
      myDescription = description;
      myVcsBranch = vcsBranch;
    }

    @NotNull
    @Override
    public String getCommit() {
      return myRevision.getRevision();
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

    @Nullable
    @Override
    public VcsRootInstance getVcsRootInstance() {
      return myRevision.getRoot();
    }

    @Nullable
    @Override
    public String getVcsBranch() {
      return myVcsBranch;
    }
  }

  private class SBuildData extends BaseBuildData implements StatusData {

    private final SBuild myBuild;
    private final BuildStatistics myBuildStatistics;

    SBuildData(@NotNull SBuild build, @NotNull BuildRevision revision, @NotNull StashBuildStatus status, @NotNull String description, @Nullable String vcsBranch) {
      super(revision, status, description, vcsBranch);
      myBuild = build;
      myBuildStatistics = myBuild.getBuildStatistics(BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);
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

    @NotNull
    @Override
    public String getBuildNumber() {
      return myBuild.getBuildNumber();
    }

    @Override
    public long getBuildDurationMs() {
      return myBuild.getDuration() * 1000;
    }

    @Override
    public BuildStatistics getBuildStatistics() {
      return myBuildStatistics;
    }
  }

  private class SBuildPromotionData extends BaseBuildData implements StatusData {

    private final BuildPromotion myBuildPromotion;

    SBuildPromotionData(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull StashBuildStatus status, @NotNull String description, @Nullable String vcsBranch) {
      super(revision, status, description, vcsBranch);
      myBuildPromotion = buildPromotion;
    }

    @NotNull
    @Override
    public String getKey() {
      return myBuildPromotion.getBuildTypeExternalId();
    }

    @NotNull
    @Override
    public String getName() {
      return myBuildPromotion.getBuildType().getName();
    }

    @NotNull
    @Override
    public String getUrl() {
      SQueuedBuild queuedBuild = myBuildPromotion.getQueuedBuild();
      if (queuedBuild != null) {
        return myLinks.getQueuedBuildUrl(queuedBuild);
      }
      return myLinks.getConfigurationHomePageUrl(myBuildPromotion.getBuildType());
    }

    @NotNull
    @Override
    public String getBuildNumber() {
      return "";
    }

    @Override
    public long getBuildDurationMs() {
      return 0;
    }

    @Override
    public BuildStatistics getBuildStatistics() {
      return null;
    }
  }

  private interface BitbucketEndpoint {
    void publishBuildStatus(@NotNull StatusData data, @NotNull String buildDescription);
    PullRequestsList getPullRequests(BuildRevision revision, @NotNull String buildDescriptor);
  }

  private abstract class BaseBitbucketEndpoint implements BitbucketEndpoint {

    @Override
    public void publishBuildStatus(@NotNull StatusData data, @NotNull String buildDescription) {
      try {
        String url = getBuildEndpointUrl(data);
        postJson(url, getUsername(), getPassword(), createBuildStatusMessage(data), null, buildDescription);
      } catch (PublisherException ex) {
        myProblems.reportProblem("Commit Status Publisher has failed to prepare a request", StashPublisher.this, buildDescription, null, ex, LOG);
      }
    }

    @Override
    public PullRequestsList getPullRequests(BuildRevision revision, @NotNull String buildDescriptor) {
      AtomicReference<PullRequestsList> pullRequestsList = new AtomicReference<>(null);
      try {
        String url = getPullRequestEndpointUrl(revision);
        if (url == null) {
          LOG.debug("No endpoint URL is provided to get pull requests for revision " + revision.getRevision());
          return null;
        }
        LoggerUtil.logRequest(getId(), HttpMethod.GET, url, null);
        IOGuard.allowNetworkCall(() -> HttpHelper.get(url, getUsername(), getPassword(), null, DEFAULT_CONNECTION_TIMEOUT, getSettings().trustStore(), new DefaultHttpResponseProcessor() {
          @Override
          public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
            super.processResponse(response);
            final String json = response.getContent();
            if (null == json) {
              throw new HttpPublisherException("Stash publisher has received no response");
            }
            PullRequestsList prList = myGson.fromJson(json, PullRequestsList.class);
            if (null == prList) {
              throw new HttpPublisherException("Stash publisher has received a malformed response");
            }
            pullRequestsList.set(prList);
          }
        }));
      } catch (Exception e) {
        myProblems.reportProblem("Can not get pull requests", StashPublisher.this, buildDescriptor, null, e, LOG);
      }
      return pullRequestsList.get();
    }


    protected abstract String getBuildEndpointUrl(final StatusData data) throws PublisherException;
    protected abstract String getPullRequestEndpointUrl(final BuildRevision revision) throws PublisherException;

    @NotNull
    protected abstract String createBuildStatusMessage(@NotNull StatusData data);
  }

  private class BuildApiEndpoint extends BaseBitbucketEndpoint implements BitbucketEndpoint {

    @Override
    protected String getBuildEndpointUrl(final StatusData data) {
      return getBaseUrl() + "/rest/build-status/1.0/commits/" + data.getCommit();
    }

    @Override
    protected String getPullRequestEndpointUrl(BuildRevision revision) {
      return null;
    }

    @NotNull
    @Override
    protected String createBuildStatusMessage(@NotNull final StatusData data) {
      Map<String, String> jsonData = new LinkedHashMap<String, String>();
      jsonData.put("state", data.getState());
      jsonData.put("key", data.getKey());
      jsonData.put("name", data.getName());
      jsonData.put("url", data.getUrl());
      jsonData.put("description", data.getDescription());
      return myGson.toJson(jsonData);
    }
  }

  private class CoreApiEndpoint extends BaseBitbucketEndpoint implements BitbucketEndpoint {

    @Override
    protected String getBuildEndpointUrl(final StatusData data) throws PublisherException {
      VcsRootInstance vcs = data.getVcsRootInstance();
      String commit = data.getCommit();
      Repository repo = getRepository(vcs, commit);
      return getBaseUrl() + "/rest/api/1.0/projects/" + repo.owner() + "/repos/" + repo.repositoryName() + "/commits/" + commit + "/builds" ;
    }

    @Override
    protected String getPullRequestEndpointUrl(BuildRevision revision) throws PublisherException {
      VcsRootInstance vcs = revision.getRoot();
      String commit = revision.getRepositoryVersion().getVersion();
      Repository repo = getRepository(vcs, commit);
      return getBaseUrl() + "/rest/api/1.0/projects/" + repo.owner() + "/repos/" + repo.repositoryName() + "/commits/" + commit + "/pull-requests";
    }

    private Repository getRepository(VcsRootInstance vcs, String commit) throws PublisherException {
      if (vcs == null)
        throw new PublisherException("No VCS root instance associated with the revision " + commit);
      String vcsUrl = vcs.getProperty("url");
      if (vcsUrl == null)
        throw new PublisherException("No VCS root fetch URL provided, revision " + commit);
      Repository repo = StashSettings.VCS_URL_PARSER.parseRepositoryUrl(vcsUrl);
      if (repo == null)
        throw new PublisherException("Failed to parse repoisotry fetch URL " + vcsUrl);
      return repo;
    }

    @NotNull
    @Override
    protected String createBuildStatusMessage(@NotNull final StatusData data) {
      JsonStashBuildStatus status = new JsonStashBuildStatus();
      status.buildNumber = data.getBuildNumber();
      status.description = data.getDescription();
      status.duration = data.getBuildDurationMs();
      status.key = data.getKey();
      status.name = data.getName();
      status.ref = data.getVcsBranch();
      status.state = data.getState();
      status.url = data.getUrl();
      BuildStatistics stats = data.getBuildStatistics();
      if (stats != null) {
        status.testResults = new JsonStashBuildStatus.StashTestStatistics();

        status.testResults.failed = stats.getFailedTestCount();
        status.testResults.skipped = stats.getMutedTestsCount() + stats.getIgnoredTestCount();
        status.testResults.successful = stats.getPassedTestCount();
      }
      return myGson.toJson(status);
    }
  }

  private static class PullRequestsList {
    private int size;
    private List<PullRequest> values;
  }

  private static class PullRequest {
    private long id;
    private String title;
    private boolean open, closed;
    private PullRequestRef fromRef, toRef;
  }

  private static class PullRequestRef {
    private String id, displayId, latestCommit;
  }
}
