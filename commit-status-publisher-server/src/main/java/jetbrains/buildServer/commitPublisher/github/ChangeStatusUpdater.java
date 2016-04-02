/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.commitPublisher.GitRepositoryParser;
import jetbrains.buildServer.commitPublisher.github.api.*;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubCommitStatusFormatterImpl;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 3:29
 */
public class ChangeStatusUpdater {

  public static final String DEFAULT_CONTEXT = "continuous-integration/teamcity";

  private static final Logger LOG = Logger.getInstance(ChangeStatusUpdater.class.getName());
  private static final UpdateChangesConstants C = new UpdateChangesConstants();

  protected final ExecutorService myExecutor;
  @NotNull
  protected final GitHubApiFactory myFactory;
  protected final WebLinks myWeb;
  protected final GitCommitStatusFormatter myStatusFormatter;
  protected String name = "GitHub";

  public ChangeStatusUpdater(@NotNull final ExecutorServices services,
                             @NotNull final GitHubApiFactory factory,
                             @NotNull final GitCommitStatusFormatter statusFormatter,
                             @NotNull final WebLinks web) {
    myFactory = factory;
    myWeb = web;
    myExecutor = services.getLowPriorityExecutorService();
    myStatusFormatter = statusFormatter;
  }

  @NotNull
  private GitHubApi getGitHubApi(@NotNull Map<String, String> params) {
    final String serverUrl = params.get(C.getServerKey());
    if (serverUrl == null || StringUtil.isEmptyOrSpaces(serverUrl)) {
      throw new IllegalArgumentException("Failed to read " + name + " URL from the feature settings");
    }

    final GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(params.get(C.getAuthenticationTypeKey()));
    switch (authenticationType) {
      case PASSWORD_AUTH:
        final String username = params.get(C.getUserNameKey());
        final String password = params.get(C.getPasswordKey());
        return myFactory.openWithCredentials(serverUrl, username, password);

      case TOKEN_AUTH:
        final String token = params.get(C.getAccessTokenKey());
        return myFactory.openWithToken(serverUrl, token);

      default:
        throw new IllegalArgumentException("Failed to parse authentication type:" + authenticationType);
    }
  }

  @Nullable
  public Handler getUpdateHandler(@NotNull VcsRootInstance root, @NotNull Map<String, String> params) {
    final GitHubApi api = getGitHubApi(params);
    final String context = params.get(C.getContextKey());

    String url = root.getProperty("url");
    if (url == null)
      return null;
    Repository repo = GitRepositoryParser.parseRepository(url);
    if (repo == null)
      return null;

    final String repositoryOwner = repo.owner();
    final String repositoryName = repo.repositoryName();
    final boolean addComments = false;

    return new Handler() {
      @NotNull
      private String getViewResultsUrl(@NotNull final SBuild build) {
        return myWeb.getViewResultsUrl(build);
      }

      public void scheduleChangeEnQueued(@NotNull RepositoryVersion version, @NotNull SQueuedBuild build) {
        scheduleChangeUpdate(version, null, "Enqueued TeamCity Build " + build.getItemId() + " + of type " + build.getBuildTypeId(), GitChangeState.Pending);
      }

      public void scheduleChangeDeQueued(@NotNull RepositoryVersion version, @NotNull SQueuedBuild build) {
        scheduleChangeUpdate(version, null, "Removed from queue TeamCity Build " + build.getItemId() + " + of type " + build.getBuildTypeId(), GitChangeState.Canceled);
      }

      public void scheduleChangeStarted(@NotNull RepositoryVersion version, @NotNull SBuild build) {
        scheduleChangeUpdate(version, build, "Started TeamCity Build " + build.getFullName(), GitChangeState.Running);
      }

      public void scheduleChangeCompeted(@NotNull RepositoryVersion version, @NotNull SBuild build) {
        LOG.debug("Status :" + build.getStatusDescriptor().getStatus().getText());
        LOG.debug("Status Priority:" + build.getStatusDescriptor().getStatus().getPriority());

        final GitChangeState status = getGitChangeState(build);
        final String text = getGitHubChangeText(build);
        scheduleChangeUpdate(version, build, "Finished TeamCity Build " + build.getFullName() + " " + text, status);
      }

      @NotNull
      private String getGitHubChangeText(@NotNull final SBuild build) {
        final String text = build.getStatusDescriptor().getText();
        if (text != null) {
          return ": " + text;
        } else {
          return "";
        }
      }

      @NotNull
      private GitChangeState getGitChangeState(@NotNull final SBuild build) {
        final Status status = build.getStatusDescriptor().getStatus();
        final byte priority = status.getPriority();

        if (priority == Status.NORMAL.getPriority()) {
          return GitChangeState.Success;
        } else if (priority == Status.FAILURE.getPriority()) {
          return GitChangeState.Failure;
        } else {
          return GitChangeState.Error;
        }
      }

      private Serializable getBuildId(@Nullable SBuild build){
        return build == null ? "<null>" : build.getBuildId();
      }

      private void scheduleChangeUpdate(@NotNull final RepositoryVersion version,
                                        @Nullable final SBuild build,
                                        @NotNull final String message,
                                        @NotNull final GitChangeState status) {
        LOG.info("Scheduling " + name + " status update for " +
                "hash: " + version.getVersion() + ", " +
                "branch: " + version.getVcsBranch() + ", " +
                "buildId: " + getBuildId(build) + ", " +
                "status: " + status);

        myExecutor.submit(ExceptionUtil.catchAll("set change status on  " + name + " ", new Runnable() {
          @NotNull
          private String getFailureText(@Nullable final TestFailureInfo failureInfo) {
            final String no_data = "<no details avaliable>";
            if (failureInfo == null) return no_data;

            final String stacktrace = failureInfo.getShortStacktrace();
            if (stacktrace == null || StringUtil.isEmptyOrSpaces(stacktrace)) return no_data;

            return stacktrace;
          }

          @NotNull
          private String getFriendlyDuration(final long seconds) {
            long second = seconds % 60;
            long minute = (seconds / 60) % 60;
            long hour = seconds / 60 / 60;

            return String.format("%02d:%02d:%02d", hour, minute, second);
          }

          @NotNull
          private String getComment(@NotNull RepositoryVersion version,
                                    @NotNull SBuild build,
                                    boolean completed,
                                    @NotNull String hash) {
            final StringBuilder comment = new StringBuilder();
            comment.append("TeamCity ");
            final SBuildType bt = build.getBuildType();
            if (bt != null) {
              comment.append(bt.getFullName());
            }
            comment.append(" [Build ");
            comment.append(build.getBuildNumber());
            comment.append("](");
            comment.append(getViewResultsUrl(build));
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

                final List<STestRun> failedTests = build.getFullStatistics().getFailedTests();
                if (!failedTests.isEmpty()) {
                  comment.append("\n### Failed tests\n");
                  comment.append("```\n");

                  for (int i = 0; i < failedTests.size(); i++) {
                    final STestRun testRun = failedTests.get(i);
                    comment.append("");
                    comment.append(testRun.getTest().getName().toString());
                    comment.append(": ");
                    comment.append(getFailureText(testRun.getFailureInfo()));
                    comment.append("\n\n");

                    if (i == 10) {
                      comment.append("\n##### there are ")
                              .append(build.getFullStatistics().getFailedTestCount() - i)
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

          @NotNull
          private String resolveCommitHash() {
            final String vcsBranch = version.getVcsBranch();
            if (vcsBranch != null && api.isPullRequestMergeBranch(vcsBranch)) {
              try {
                final String hash = api.findPullRequestCommit(repositoryOwner, repositoryName, vcsBranch);
                if (hash == null) {
                  throw new IOException("Failed to find head hash for commit from " + vcsBranch);
                }
                LOG.info("Resolved " + name + " change commit for " + vcsBranch + " to point to pull request head for " +
                        "hash: " + version.getVersion() + ", " +
                        "newHash: " + hash + ", " +
                        "branch: " + version.getVcsBranch() + ", " +
                        "buildId: " + getBuildId(build) + ", " +
                        "status: " + status);
                return hash;
              } catch (IOException e) {
                LOG.warn("Failed to find status update hash for " + vcsBranch + " for repository " + repositoryName);
              }
            }
            return version.getVersion();
          }

          public void run() {
            final String hash = resolveCommitHash();
            try {
              api.setChangeStatus(
                      repositoryOwner,
                      repositoryName,
                      hash,
                      myStatusFormatter.getStatus(status),
                      (build == null ? null : getViewResultsUrl(build)),
                      message,
                      context
              );
              LOG.info("Updated " + name + " status for hash: " + hash + ", buildId: " + getBuildId(build) + ", status: " + status);
            } catch (IOException e) {
              LOG.warn("Failed to update " + name + " status for hash: " + hash + ", buildId: " + getBuildId(build) + ", status: " + status + ". " + e.getMessage(), e);
            }
            if (addComments) {
              try {
                api.postComment(
                        repositoryOwner,
                        repositoryName,
                        hash,
                        getComment(version, build, status != GitChangeState.Pending, hash)
                );
                LOG.info("Added comment to " + name + " commit: " + hash + ", buildId: " + build.getBuildId() + ", status: " + status);
              } catch (IOException e) {
                LOG.warn("Failed add " + name + " comment for branch: " + version.getVcsBranch() + ", buildId: " + getBuildId(build) + ", status: " + status + ". " + e.getMessage(), e);
              }
            }
          }
        }));
      }
    };
  }

  public static interface Handler {
    void scheduleChangeEnQueued(@NotNull final RepositoryVersion hash, @NotNull final SQueuedBuild build);
    void scheduleChangeDeQueued(@NotNull final RepositoryVersion hash, @NotNull final SQueuedBuild build);
    void scheduleChangeStarted(@NotNull final RepositoryVersion hash, @NotNull final SBuild build);
    void scheduleChangeCompeted(@NotNull final RepositoryVersion hash, @NotNull final SBuild build);
  }
}
