package jetbrains.buildServer.commitPublisher.github;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class GitHubPublisher extends BaseCommitStatusPublisher {

  private static final Logger LOG = Logger.getInstance(GitHubPublisher.class.getName());

  private final ChangeStatusUpdater myUpdater;

  GitHubPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                  @NotNull ChangeStatusUpdater updater,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
    myUpdater = updater;
  }

  @NotNull
  public String toString() {
    return "github";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @Override
  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, true);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }


  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(revision.getRoot(), getParams(build), this);

    if (isStarting && !h.shouldReportOnStart()) return;
    if (!isStarting && !h.shouldReportOnFinish()) return;

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    if (isStarting) {
      h.scheduleChangeStarted(revision.getRepositoryVersion(), build);
    } else {
      h.scheduleChangeCompleted(revision.getRepositoryVersion(), build);
    }
  }


  @NotNull
  private Map<String, String> getParams(@NotNull SBuild build) {
    String context = getCustomContextFromParameter(build);
    if (context == null)
      context = getDefaultContext(build);
    Map<String, String> result = new HashMap<String, String>(myParams);
    result.put(Constants.GITHUB_CONTEXT, context);
    return result;
  }


  @NotNull
  private String getDefaultContext(@NotNull SBuild build) {
    SBuildType buildType = build.getBuildType();
    if (buildType != null) {
      return String.format("%s (%s)", buildType.getName(), buildType.getProject().getName());
    } else {
      return "<Removed build configuration>";
    }
  }


  @Nullable
  private String getCustomContextFromParameter(@NotNull SBuild build) {
    String value = build.getParametersProvider().get(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM);
    if (value == null) {
      return null;
    } else {
      return build.getValueResolver().resolve(value).getResult();
    }
  }
}
