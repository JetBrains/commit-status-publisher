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

package jetbrains.buildServer.commitPublisher.github;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApi;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.api.GitHubChangeState;
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
  private final WebLinks myWeb;

  public ChangeStatusUpdater(@NotNull final GitHubApiFactory factory,
                             @NotNull final WebLinks web,
                             @NotNull final VcsModificationHistory vcsModificationHistory) {
    myFactory = factory;
    myWeb = web;
    myModificationHistory = vcsModificationHistory;
  }

  @NotNull
  private GitHubApi getGitHubApi(@NotNull Map<String, String> params) {
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

      default:
        throw new IllegalArgumentException("Failed to parse authentication type:" + authenticationType);
    }
  }

  void testConnection(@NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    getGitHubApi(params).testConnection(parseRepository(root));
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
  Handler getUpdateHandler(@NotNull VcsRoot root,
                           @NotNull Map<String, String> params,
                           @NotNull final GitHubPublisher publisher) {

    return new Handler() {

      public void changeStarted(@NotNull BuildRevision revision, @NotNull SBuild build) throws PublisherException {
        doChangeUpdate(revision, build, "TeamCity build started", GitHubChangeState.Pending);
      }

      public void changeCompleted(@NotNull BuildRevision revision, @NotNull SBuild build) throws PublisherException {
        LOG.debug("Status :" + build.getStatusDescriptor().getStatus().getText());
        LOG.debug("Status Priority:" + build.getStatusDescriptor().getStatus().getPriority());

        final GitHubChangeState status = getGitHubChangeState(build);
        final String text = getGitHubChangeText(build);
        doChangeUpdate(revision, build, text, status);
      }

      @NotNull
      private String getGitHubChangeText(@NotNull SBuild build) {
        if (build.getBuildStatus().isSuccessful()) {
          return "TeamCity build finished";
        } else {
          return "TeamCity build failed";
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

      private void doChangeUpdate(@NotNull final BuildRevision revision,
                                  @NotNull final SBuild build,
                                  @NotNull final String message,
                                  @NotNull final GitHubChangeState targetStatus) throws PublisherException {
        final RepositoryVersion version = revision.getRepositoryVersion();
        LOG.info("Scheduling GitHub status update for " +
                 "hash: " + version.getVersion() + ", " +
                 "branch: " + version.getVcsBranch() + ", " +
                 "buildId: " + build.getBuildId() + ", " +
                 "status: " + targetStatus);

        Repository repo = parseRepository(root);

        GitHubStatusUpdater statusUpdater = new GitHubStatusUpdater(params, publisher, false);
        statusUpdater.update(revision, build, message, targetStatus, repo);
      }
    };
  }

  private abstract class GitHubCommonStatusUpdater {
    private static final String DEFAULT_CONTEXT = "continuous-integration/teamcity";

    protected final GitHubPublisher myPublisher;
    protected final GitHubApi myApi;
    protected final String myContext;
    protected final boolean myAddComment;

    GitHubCommonStatusUpdater(Map<String, String> params, GitHubPublisher publisher, boolean addComment) {
      myPublisher = publisher;
      myAddComment = addComment;
      String ctx = params.get(Constants.GITHUB_CONTEXT);
      myContext = StringUtil.isEmpty(ctx) ? DEFAULT_CONTEXT : ctx;
      myApi = getGitHubApi(params);
    }

    @NotNull
    protected String resolveCommitHash(RepositoryVersion myVersion, Repository repo, Long buildId, GitHubChangeState myTargetStatus) {
      final String vcsBranch = myVersion.getVcsBranch();
      if (vcsBranch != null && myApi.isPullRequestMergeBranch(vcsBranch)) {
        try {
          final String hash = myApi.findPullRequestCommit(repo.owner(), repo.repositoryName(), vcsBranch);
          if (hash == null) {
            throw new IOException("Failed to find head hash for commit from " + vcsBranch);
          }
          LOG.info("Resolved GitHub change commit for " + vcsBranch + " to point to pull request head for " +
                   "hash: " + myVersion.getVersion() + ", " +
                   "newHash: " + hash + ", " +
                   "branch: " + myVersion.getVcsBranch() + ", " +
                   "buildId: " + buildId + ", " +
                   "status: " + myTargetStatus);
          return hash;
        } catch (Exception e) {
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
                                    Long buildId,
                                    GitHubChangeState targetStatus) {
      if (!(hash.equals(version.getVersion()) ||
            myModificationHistory.getModificationsOrder(root, hash, version.getVersion())
                                 .equals(VcsModificationOrder.BEFORE))) {
        LOG.info("GitHub status for pull request commit has not been updated. The head branch hash: " + hash
                 + " does not correspond to the merge branch hash " + version.getVersion() + " any longer (buildId: " + buildId + ", status: " + targetStatus + ")");
        return true;
      }
      return false;
    }

    protected void addComment(@NotNull Repository repo, @NotNull String hash, @NotNull String comment, Long buildId, GitHubChangeState targetStatus) throws IOException {
      myApi.postComment(
        repo.owner(),
        repo.repositoryName(),
        hash,
        comment
      );
      LOG.info("Added comment to GitHub commit: " + hash + ", buildId: " + buildId + ", status: " + targetStatus);
    }
  }

  private class GitHubStatusUpdater extends GitHubCommonStatusUpdater {

    GitHubStatusUpdater(Map<String, String> params, GitHubPublisher publisher, boolean addComment) {
      super(params, publisher, addComment);
    }

    public void update(BuildRevision revision, SBuild build, String message, GitHubChangeState targetStatus, Repository repo) {
      final RepositoryVersion version = revision.getRepositoryVersion();
      final String hash = resolveCommitHash(version, repo, build.getBuildId(), targetStatus);
      if (isHashInvalid(hash, version, revision.getRoot(), build.getBuildId(), targetStatus)) {
        return;
      }

      final CommitStatusPublisherProblems problems = myPublisher.getProblems();
      try {
        changeStatus(build, repo, hash, version, message, targetStatus);
      } catch (IOException e) {
        problems.reportProblem(String.format("Commit Status Publisher error. GitHub status: '%s'", targetStatus), myPublisher, LogUtil.describe(build), myPublisher.getServerUrl(), e, LOG);
      }

      if (myAddComment) {
        String comment = getComment(build, targetStatus != GitHubChangeState.Pending);
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
                              GitHubChangeState targetStatus) throws IOException {
      boolean prMergeBranch = !hash.equals(version.getVersion());
      String url = myWeb.getViewResultsUrl(build);
      myApi.setChangeStatus(
        repo.owner(),
        repo.repositoryName(),
        hash,
        targetStatus,
        url,
        message,
        prMergeBranch ? myContext + " - merge" : myContext
      );
      LOG.info("Updated GitHub status for hash: " + hash + ", buildId: " + build.getBuildId() + ", status: " + targetStatus);
    }

    @NotNull
    private String getComment(@NotNull SBuild build, boolean completed) {
      final StringBuilder comment = new StringBuilder();
      comment.append("TeamCity ");
      final SBuildType bt = build.getBuildType();
      if (bt != null) {
        comment.append(bt.getFullName());
      }
      comment.append(" [Build ");
      comment.append(build.getBuildNumber());
      comment.append("](");
      comment.append(myWeb.getViewResultsUrl(build));
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
  }

  interface Handler {
    void changeStarted(@NotNull final BuildRevision revision, @NotNull final SBuild build) throws PublisherException;
    void changeCompleted(@NotNull final BuildRevision revision, @NotNull final SBuild build) throws PublisherException;
  }
}
