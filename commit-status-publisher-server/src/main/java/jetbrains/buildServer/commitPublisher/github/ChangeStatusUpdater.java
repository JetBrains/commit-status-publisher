

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.github;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApi;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.api.GitHubChangeState;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.CombinedCommitStatus;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.CommitStatus;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.vcs.VcsModificationOrder;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 3:29
 */
public class ChangeStatusUpdater {
  private static final UpdateChangesConstants C = new UpdateChangesConstants();
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  private final VcsModificationHistory myModificationHistory;

  @NotNull
  private final GitHubApiFactory myFactory;

  public ChangeStatusUpdater(@NotNull final GitHubApiFactory factory,
                             @NotNull final VcsModificationHistory vcsModificationHistory) {
    myFactory = factory;
    myModificationHistory = vcsModificationHistory;
  }


  @NotNull
  private GitHubApi getGitHubApi(@NotNull Map<String, String> params, @NotNull SProject project, @NotNull VcsRoot root) {
    final String serverUrl = params.get(C.getServerKey());
    if (serverUrl == null || StringUtil.isEmptyOrSpaces(serverUrl)) {
      throw new IllegalArgumentException("Failed to read GitHub URL from the feature settings");
    }

    final GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(params.get(C.getAuthenticationTypeKey()));
    switch (authenticationType) {
      case PASSWORD_AUTH:
        final String username = params.get(C.getUserNameKey());
        String password = params.get(C.getPasswordKey());
        if (password == null) {
          password = params.get(Constants.GITHUB_PASSWORD_DEPRECATED);
        }
        return myFactory.openGitHubForUser(serverUrl, username, password);

      case TOKEN_AUTH:
        final String token = params.get(C.getAccessTokenKey());
        return myFactory.openGitHubForToken(serverUrl, token);

      case STORED_TOKEN:
        final String tokenId = params.get(C.getTokenIdKey());
        return myFactory.openGitHubForStoredToken(serverUrl, tokenId, project);
      case VCS_ROOT:
        return getGitHubApiForVcsRootCredentials(project, root, serverUrl);
      default:
        throw new IllegalArgumentException("Failed to parse authentication type:" + authenticationType);
    }
  }

  @NotNull
  private GitHubApi getGitHubApiForVcsRootCredentials(@NotNull SProject project, @NotNull VcsRoot root, @NotNull String serverUrl) {
    String vcsRootAuthType = root.getProperty("authMethod");
    if (vcsRootAuthType == null) {
      throw new IllegalArgumentException("Failed to parse VCS authentication type");
    }

    switch (vcsRootAuthType) {
      case "ACCESS_TOKEN": //refreshable token auth
        final String tokenId = root.getProperty("tokenId");
        if (tokenId == null) {
          throw new IllegalArgumentException("Failed to get tokenId in VCS authentication type");
        }
        return myFactory.openGitHubForStoredToken(serverUrl, tokenId, project);
      case "PASSWORD": //token auth
        String password = root.getProperty("secure:password");
        if (password == null) {
          throw new IllegalArgumentException("Failed to get username/token in VCS authentication type");
        }
        String username = root.getProperty("username");
        if (username == null)
          username = "oauth2";
        return myFactory.openGitHubForUser(serverUrl, username, password);
      default:
        throw new IllegalArgumentException("Failed to parse VCS authentication type:" + vcsRootAuthType);
    }
  }

  void testConnection(@NotNull SProject project, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    getGitHubApi(params, project, root).testConnection(parseRepository(root));
  }

  @NotNull
  private Repository parseRepository(VcsRoot root) throws PublisherException {
    String url = root.getProperty("url");
    Repository repo;
    if (null == url) {
      repo = null;
    } else {
      repo = VCS_URL_PARSER.parseRepositoryUrl(url);
    }
    if (null == repo)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    return repo;
  }

  @NotNull
  Handler getHandler(@NotNull VcsRoot root,
                     @NotNull Map<String, String> params,
                     @NotNull final GitHubPublisher publisher) {

    return new Handler() {

      public void changeStarted(@NotNull BuildRevision revision, @NotNull SBuild build, @NotNull String viewUrl) throws PublisherException {
        doChangeUpdate(revision, build, DefaultStatusMessages.BUILD_STARTED, GitHubChangeState.Pending, viewUrl);
      }

      public void changeCompleted(@NotNull BuildRevision revision, @NotNull SBuild build, @NotNull String viewUrl) throws PublisherException {
        LOG.debug("Status :" + build.getStatusDescriptor().getStatus().getText());
        LOG.debug("Status Priority:" + build.getStatusDescriptor().getStatus().getPriority());

        final GitHubChangeState status = getGitHubChangeState(build);
        final String text = getGitHubChangeText(build);
        doChangeUpdate(revision, build, text, status, viewUrl);
      }

      @Override
      public boolean changeQueued(@NotNull BuildRevision revision, @NotNull BuildPromotion buildPromotion,
                                  @NotNull AdditionalTaskInfo additionalTaskInfo, @NotNull String viewUrl) throws PublisherException {
        return doQueuedChangeUpdate(revision, buildPromotion, additionalTaskInfo, viewUrl, false);
      }

      @Override
      public boolean changeRemovedFromQueue(@NotNull BuildRevision revision, @NotNull BuildPromotion buildPromotion,
                                            @NotNull AdditionalTaskInfo additionalTaskInfo, @NotNull String viewUrl) throws PublisherException {
        return doQueuedChangeUpdate(revision, buildPromotion, additionalTaskInfo, viewUrl, true);
      }

      @NotNull
      private String getGitHubChangeText(@NotNull SBuild build) {
        if (build.getBuildStatus().isSuccessful()) {
          return DefaultStatusMessages.BUILD_FINISHED;
        } else {
          return DefaultStatusMessages.BUILD_FAILED;
        }
      }

      @NotNull
      private GitHubChangeState getGitHubChangeState(@NotNull final SBuild build) {
        final Status status = build.getStatusDescriptor().getStatus();
        final byte priority = status.getPriority();

        if (priority == Status.NORMAL.getPriority()) {
          return GitHubChangeState.Success;
        } else if (priority == Status.FAILURE.getPriority()) {
          return GitHubChangeState.Failure;
        } else {
          return GitHubChangeState.Error;
        }
      }

      @Override
      public CommitStatus getStatus(@NotNull BuildRevision revision) throws PublisherException {
        RepositoryVersion version = revision.getRepositoryVersion();
        String buildContext = params.get(Constants.GITHUB_CONTEXT);
        LOG.debug("Requesting statuses for " +
                  "hash: " + version.getVersion() + ", " +
                  "branch: " + version.getVcsBranch() + ", " +
                  "build: " + buildContext);

        Repository repo = parseRepository(root);
        GitHubStatusClient statusClient = new GitHubStatusClient(params, publisher, root);
        try {
          return statusClient.getStatus(revision, repo);
        } catch (IOException e) {
          publisher.getProblems().reportProblem(String.format("Commit Status Publisher error. Can not receive status for revision: %s", revision.getRevision()), publisher,
                                                buildContext, publisher.getServerUrl(), e, LOG);
        }
        return null;
      }

      @Override
      public Collection<CommitStatus> getStatuses(@NotNull BuildRevision revision) throws PublisherException {
        RepositoryVersion version = revision.getRepositoryVersion();
        String buildContext = params.get(Constants.GITHUB_CONTEXT);
        LOG.debug("Requesting statuses for " +
                  "hash: " + version.getVersion() + ", " +
                  "branch: " + version.getVcsBranch() + ", " +
                  "build: " + buildContext);

        Repository repo = parseRepository(root);
        GitHubStatusClient statusClient = new GitHubStatusClient(params, publisher, root);

        final int perPage = 25;
        int page = 1;
        Collection<CommitStatus> result = new ArrayList<>();
        boolean keepLoading;
        final int statusesThreshold = TeamCityProperties.getInteger(Constants.STATUSES_TO_LOAD_THRESHOLD_PROPERTY, Constants.STATUSES_TO_LOAD_THRESHOLD_DEFAULT_VAL);

        do {
          Collection<CommitStatus> statuses;
          try {
            statuses = statusClient.getStatuses(revision, repo, perPage, page);
          } catch (IOException | PublisherException e) {
            publisher.getProblems().reportProblem(String.format("Commit Status Publisher error. Can not receive status for revision: %s", revision.getRevision()), publisher,
                                                  buildContext, publisher.getServerUrl(), e, LOG);
            if (e instanceof PublisherException) {
              throw (PublisherException)e;
            }
            return Collections.emptyList();
          }
          if (statuses == null) return result;
          result.addAll(statuses);
          keepLoading = !statuses.isEmpty() &&
                        result.size() < statusesThreshold &&
                        statuses.stream().noneMatch(status -> buildContext.equals(status.context));
        } while (keepLoading);
        return result;
      }

      private void doChangeUpdate(@NotNull final BuildRevision revision,
                                  @NotNull final SBuild build,
                                  @NotNull final String message,
                                  @NotNull final GitHubChangeState targetStatus,
                                  @NotNull String viewUrl) throws PublisherException {
        final RepositoryVersion version = revision.getRepositoryVersion();
        LOG.debug("Scheduling GitHub status update for " +
                 "hash: " + version.getVersion() + ", " +
                 "branch: " + version.getVcsBranch() + ", " +
                 "buildId: " + build.getBuildId() + ", " +
                 "status: " + targetStatus);

        Repository repo = parseRepository(root);

        GitHubStatusClient statusClient = new GitHubStatusClient(params, publisher, root);
        statusClient.update(revision, build, message, targetStatus, repo, viewUrl);
      }

      private boolean doQueuedChangeUpdate(@NotNull BuildRevision revision,
                                           @NotNull BuildPromotion buildPromotion,
                                           @NotNull AdditionalTaskInfo additionalTaskInfo,
                                           @NotNull String viewUrl,
                                           boolean deletedFromQueue) throws PublisherException {
        final RepositoryVersion version = revision.getRepositoryVersion();
        final GitHubChangeState targetStatus = deletedFromQueue ? GitHubChangeState.Failure : GitHubChangeState.Pending;
        LOG.debug("Scheduling GitHub status update for " +
                 "hash: " + version.getVersion() + ", " +
                 "branch: " + version.getVcsBranch() + ", " +
                 "buildId: " + buildPromotion.getId() + ", " +
                 "status: " + targetStatus);

        Repository repo = parseRepository(root);

        GitHubQueuedStatusClient statusClient = new GitHubQueuedStatusClient(params, publisher, root);
        return statusClient.update(revision, buildPromotion, targetStatus, repo, additionalTaskInfo, viewUrl);
      }
    };
  }

  private abstract class GitHubCommonStatusClient {
    private static final String DEFAULT_CONTEXT = "continuous-integration/teamcity";

    protected final GitHubPublisher myPublisher;
    protected final GitHubApi myApi;
    protected final String myContext;

    GitHubCommonStatusClient(Map<String, String> params, GitHubPublisher publisher, @NotNull VcsRoot root) {
      myPublisher = publisher;
      String ctx = params.get(Constants.GITHUB_CONTEXT);
      myContext = StringUtil.isEmpty(ctx) ? DEFAULT_CONTEXT : ctx;
      myApi = getGitHubApi(params, publisher.getBuildType().getProject(), root);
    }

    @NotNull
    protected String resolveCommitHash(RepositoryVersion myVersion, Repository repo, GitHubChangeState myTargetStatus, String buildIdentificator, @Nullable AtomicBoolean shouldRetry) {
      final String vcsBranch = myVersion.getVcsBranch();
      if (vcsBranch != null && myApi.isPullRequestMergeBranch(vcsBranch)) {
        try {
          final String hash = myApi.findPullRequestCommit(repo.owner(), repo.repositoryName(), vcsBranch);
          if (hash == null) {
            throw new IOException("Failed to find head hash for commit from " + vcsBranch);
          }
          LOG.debug("Resolved GitHub change commit for " + vcsBranch + " to point to pull request head for " +
                   "hash: " + myVersion.getVersion() + ", " +
                   "newHash: " + hash + ", " +
                   "branch: " + myVersion.getVcsBranch() + ", " +
                   "status: " + myTargetStatus + ", " +
                   buildIdentificator);
          return hash;
        } catch (Exception e) {
          if (shouldRetry != null && e instanceof PublisherException && ((PublisherException)e).shouldRetry()) {
            shouldRetry.set(true);
          }
          LOG.warn("Failed to find status update hash for " + vcsBranch + " for repository " + repo.repositoryName());
        }
      }
      return myVersion.getVersion();
    }

    @NotNull
    protected String getFriendlyDuration(final long seconds) {
      long second = seconds % 60;
      long minute = (seconds / 60) % 60;
      long hour = seconds / 60 / 60;

      return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    protected boolean isHashInvalid(@NotNull String hash,
                                    @NotNull RepositoryVersion version,
                                    @NotNull VcsRootInstance root,
                                    @NotNull String buildIdentificator) {
      if (!(hash.equals(version.getVersion()) ||
            myModificationHistory.getModificationsOrder(root, hash, version.getVersion())
                                 .equals(VcsModificationOrder.BEFORE))) {
        LOG.info("GitHub status for pull request commit has not been updated. The head branch hash: " + hash
                 + " does not correspond to the merge branch hash " + version.getVersion() + " any longer (" + buildIdentificator + ")");
        return true;
      }
      return false;
    }

    @Nullable
    public CommitStatus getStatus(@NotNull BuildRevision revision,  @NotNull Repository repo) throws IOException, PublisherException {
      final RepositoryVersion version = revision.getRepositoryVersion();
      final String hash = resolveCommitHash(version, repo, null, myContext, null);
      if (isHashInvalid(hash, version, revision.getRoot(), myContext)) {
        return null;
      }
      final int perPage = 30;
      int page = 0;
      int totalStatuses;

      do {
        page++;
        CombinedCommitStatus combinedCommitStatus = myApi.readChangeCombinedStatus(repo.owner(), repo.repositoryName(), hash, perPage, page);
        if (combinedCommitStatus.statuses == null || combinedCommitStatus.statuses.isEmpty()) {
          LOG.debug(String.format("No statuses received from GitHub for repository \"%s/%s\" hash %s", repo.owner(), repo.repositoryName(), hash));
          break;
        }
        Optional<CommitStatus> requiredStatus = combinedCommitStatus.statuses.stream().filter(status -> myContext.equals(status.context)).findAny();
        if (requiredStatus.isPresent()) {
          return requiredStatus.get();
        }
        totalStatuses = combinedCommitStatus.total_count != null ? combinedCommitStatus.total_count : 0;
      } while (totalStatuses > page * perPage);
      return null;
    }

    @Nullable
    public Collection<CommitStatus> getStatuses(@NotNull BuildRevision revision, @NotNull Repository repo,
                                                @NotNull int perPage, @NotNull int page) throws IOException, PublisherException {
      final RepositoryVersion version = revision.getRepositoryVersion();
      final AtomicBoolean shouldRetry = new AtomicBoolean();
      final String hash = resolveCommitHash(version, repo, null, myContext, shouldRetry);
      if (isHashInvalid(hash, version, revision.getRoot(), myContext)) {
        if (shouldRetry.get()) {
          throw new PublisherException("Failed to resolve commit hash for GitHub").setShouldRetry();
        }
        return null;
      }

      CombinedCommitStatus combinedCommitStatus = myApi.readChangeCombinedStatus(repo.owner(), repo.repositoryName(), hash, perPage, page);
      if (combinedCommitStatus.statuses == null || combinedCommitStatus.statuses.isEmpty()) {
        LOG.debug(String.format("No statuses received from GitHub for repository \"%s/%s\" hash %s", repo.owner(), repo.repositoryName(), hash));
        return null;
      }

      for (CommitStatus status : combinedCommitStatus.statuses) {
        if (myContext.equals(status.context))
          return combinedCommitStatus.statuses;
      }
      return Collections.emptyList();
    }
  }

  private class GitHubQueuedStatusClient extends GitHubCommonStatusClient {

    GitHubQueuedStatusClient(Map<String, String> params, GitHubPublisher publisher, @NotNull VcsRoot root) {
      super(params, publisher, root);
    }

    public boolean update(@NotNull BuildRevision revision,
                          @NotNull BuildPromotion buildPromotion,
                          @NotNull GitHubChangeState targetStatus,
                          @NotNull Repository repo,
                          @NotNull AdditionalTaskInfo additionalTaskInfo,
                          @NotNull String viewUrl) throws PublisherException {
      final RepositoryVersion version = revision.getRepositoryVersion();
      SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
      String buildIdentificator = queuedBuild != null ? "queuedBuildId: " + queuedBuild.getItemId() : "buildPromotionId: " + buildPromotion.getId();
      final AtomicBoolean shouldRetry = new AtomicBoolean();
      final String hash = resolveCommitHash(version, repo, targetStatus, buildIdentificator, shouldRetry);
      if (isHashInvalid(hash, version, revision.getRoot(), buildIdentificator)) {
        if (shouldRetry.get()) {
          throw new PublisherException("Failed to resolve commit hash for GitHub").setShouldRetry();
        }
        return false;
      }

      String compiledMessage = additionalTaskInfo.getComment();
      boolean prMergeBranch = !hash.equals(version.getVersion());
      try {
        myApi.setChangeStatus(
          repo.owner(),
          repo.repositoryName(),
          hash,
          targetStatus,
          viewUrl,
          compiledMessage,
          prMergeBranch ? myContext + " - merge" : myContext
        );
        LOG.debug("Updated GitHub status for hash: " + hash + ", buildId: " + buildPromotion.getAssociatedBuildId() + ", status: " + targetStatus);
      } catch (PublisherException | IOException e) {
        throw new PublisherException("Commit Status Publisher error. " + e, e);
      }
      return true;
    }
  }

  private class GitHubStatusClient extends GitHubCommonStatusClient {
    private final boolean myAddComment = false;

    GitHubStatusClient(Map<String, String> params, GitHubPublisher publisher, @NotNull VcsRoot root) {
      super(params, publisher, root);
    }

    public void update(BuildRevision revision, SBuild build, String message, GitHubChangeState targetStatus, Repository repo, String viewUrl) throws PublisherException{
      final RepositoryVersion version = revision.getRepositoryVersion();
      String buildIdentififcator = "buildId: " + build.getBuildId();
      final AtomicBoolean shouldRetry = new AtomicBoolean();
      final String hash = resolveCommitHash(version, repo, targetStatus, buildIdentififcator, shouldRetry);
      if (isHashInvalid(hash, version, revision.getRoot(), buildIdentififcator)) {
        if (shouldRetry.get()) {
          throw new PublisherException("Failed to resolve commit hash for GitHub").setShouldRetry();
        }
        return;
      }

      final CommitStatusPublisherProblems problems = myPublisher.getProblems();
      try {
        changeStatus(build, repo, hash, version, message, targetStatus, viewUrl);
      } catch (IOException | PublisherException e) {
        throw new PublisherException("Commit Status Publisher error. " + e, e);
      }

      if (myAddComment) {
        String comment = getComment(build, targetStatus != GitHubChangeState.Pending, viewUrl);
        try {
          addComment(repo, hash, comment, build.getBuildId(), targetStatus);
        } catch (IOException e) {
          problems.reportProblem("Commit Status Publisher has failed to add a comment", myPublisher, LogUtil.describe(build), null, e, LOG);
        }
      }
    }

    private void changeStatus(SBuild build,
                              Repository repo,
                              String hash,
                              RepositoryVersion version,
                              String message,
                              GitHubChangeState targetStatus,
                              String viewUrl) throws IOException, PublisherException {
      boolean prMergeBranch = !hash.equals(version.getVersion());
      myApi.setChangeStatus(
        repo.owner(),
        repo.repositoryName(),
        hash,
        targetStatus,
        viewUrl,
        message,
        prMergeBranch ? myContext + " - merge" : myContext
      );
      LOG.debug("Updated GitHub status for hash: " + hash + ", buildId: " + build.getBuildId() + ", status: " + targetStatus);
    }

    @NotNull
    private String getComment(@NotNull SBuild build, boolean completed, String viewUrl) {
      final StringBuilder comment = new StringBuilder();
      comment.append("TeamCity ");
      final SBuildType bt = build.getBuildType();
      if (bt != null) {
        comment.append(bt.getFullName());
      }
      comment.append(" [Build ");
      comment.append(build.getBuildNumber());
      comment.append("](");
      comment.append(viewUrl);
      comment.append(") ");

      if (completed) {
        comment.append("outcome was **").append(build.getStatusDescriptor().getStatus().getText()).append("**");
      } else {
        comment.append("is now running");
      }

      comment.append("\n");

      final String text = build.getStatusDescriptor().getText();
      if (completed && text != null) {
        comment.append("Summary: ");
        comment.append(text);
        comment.append(" Build time: ");
        comment.append(getFriendlyDuration(build.getDuration()));

        if (build.getBuildStatus() != Status.NORMAL) {

          BuildStatistics stats = build.getBuildStatistics(BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);
          final List<STestRun> failedTests = stats.getFailedTests();
          if (!failedTests.isEmpty()) {
            comment.append("\n### Failed tests\n");
            comment.append("```\n");

            for (int i = 0; i < failedTests.size(); i++) {
              final STestRun testRun = failedTests.get(i);
              comment.append(testRun.getTest().getName());
              comment.append("\n");

              if (i == 10) {
                comment.append("\n##### there are ")
                       .append(stats.getFailedTestCount() - i)
                       .append(" more failed tests, see build details\n");
                break;
              }
            }
            comment.append("```\n");
          }
        }
      }

      return comment.toString();
    }

    private void addComment(@NotNull Repository repo, @NotNull String hash, @NotNull String comment, Long buildId, GitHubChangeState targetStatus) throws IOException {
      myApi.postComment(
        repo.owner(),
        repo.repositoryName(),
        hash,
        comment
      );
      LOG.debug("Added comment to GitHub commit: " + hash + ", buildId: " + buildId + ", status: " + targetStatus);
    }
  }

  interface Handler {
    void changeStarted(@NotNull final BuildRevision revision, @NotNull final SBuild build, @NotNull String viewUrl) throws PublisherException;
    void changeCompleted(@NotNull final BuildRevision revision, @NotNull final SBuild build, @NotNull String viewUrl) throws PublisherException;
    boolean changeQueued(@NotNull final BuildRevision revision, @NotNull final BuildPromotion build, @NotNull AdditionalTaskInfo additionalTaskInfo, @NotNull String viewUrl) throws PublisherException;
    boolean changeRemovedFromQueue(@NotNull final BuildRevision revision, @NotNull final BuildPromotion build, @NotNull AdditionalTaskInfo additionalTaskInfo, @NotNull String viewUrl) throws PublisherException;
    CommitStatus getStatus(@NotNull final BuildRevision revision) throws PublisherException;
    Collection<CommitStatus> getStatuses(@NotNull final  BuildRevision revision) throws PublisherException;
  }
}