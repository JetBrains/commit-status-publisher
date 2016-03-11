package jetbrains.buildServer.commitPublisher.github;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class GitHubPublisher extends BaseCommitStatusPublisher {

  private static final Logger LOG = Logger.getInstance(GitHubPublisher.class.getName());

  private final ChangeStatusUpdater myUpdater;

  public GitHubPublisher(@NotNull ChangeStatusUpdater updater,
                         @NotNull Map<String, String> params) {
    super(params);
    myUpdater = updater;
  }

  @NotNull
  public String toString() {
    return "github";
  }

  @Override
  public String getId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @Override
  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    updateBuildStatus(build, revision, true);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    updateBuildStatus(build, revision, false);
    return true;
  }


  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) {
    final ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(revision.getRoot(), myParams);

    if (h == null)
      return;

    if (isStarting && !h.shouldReportOnStart()) return;
    if (!isStarting && !h.shouldReportOnFinish()) return;

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    if (isStarting) {
      h.scheduleChangeStarted(revision.getRepositoryVersion(), build);
    } else {
      h.scheduleChangeCompeted(revision.getRepositoryVersion(), build);
    }
  }
}
