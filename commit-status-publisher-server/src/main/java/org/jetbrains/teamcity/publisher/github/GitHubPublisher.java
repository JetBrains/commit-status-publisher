package org.jetbrains.teamcity.publisher.github;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.publisher.BaseCommitStatusPublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class GitHubPublisher extends BaseCommitStatusPublisher {

  private static final Logger LOG = Loggers.SERVER;

  private final ChangeStatusUpdater myUpdater;

  public GitHubPublisher(@NotNull ChangeStatusUpdater updater,
                         @NotNull Map<String, String> params) {
    super(params);
    myUpdater = updater;
  }

  @Override
  public void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    updateBuildStatus(build, true);
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    updateBuildStatus(build, false);
  }

  @Override
  public void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    updateBuildStatus(build, false);
  }


  private void updateBuildStatus(@NotNull final SBuild build, boolean isStarting) {
    final ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(myParams);
    if (isStarting && !h.shouldReportOnStart()) return;
    if (!isStarting && !h.shouldReportOnFinish()) return;

    final Collection<BuildRevision> changes = getLatestChangesHash(build);
    if (changes.isEmpty()) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
    }

    for (BuildRevision e : changes) {
      if (isStarting) {
        h.scheduleChangeStarted(e.getRepositoryVersion(), build);
      } else {
        h.scheduleChangeCompeted(e.getRepositoryVersion(), build);
      }
    }
  }


  @NotNull
  private Collection<BuildRevision> getLatestChangesHash(@NotNull final SBuild build) {
    final Collection<BuildRevision> result = new ArrayList<BuildRevision>();
    for (BuildRevision rev : build.getRevisions()) {
      if (!"jetbrains.git".equals(rev.getRoot().getVcsName())) continue;

      LOG.debug("Found revision to report status to GitHub: " + rev.getRevision() + ", branch: " + rev.getRepositoryVersion().getVcsBranch() + " from root " + rev.getRoot().getName());
      result.add(rev);
    }
    return result;
  }
}
