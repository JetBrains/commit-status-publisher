package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class BaseCommitStatusPublisher implements CommitStatusPublisher {

  protected final Map<String, String> myParams;
  private int myConnectionTimeout = 300 * 1000;
  protected CommitStatusPublisherProblems myProblems;
  protected SBuildType myBuildType;
  private String myBuildFeatureId;

  protected BaseCommitStatusPublisher(@NotNull SBuildType buildType,@NotNull String buildFeatureId,
                                      @NotNull Map<String, String> params,
                                      @NotNull CommitStatusPublisherProblems problems) {
    myParams = params;
    myProblems = problems;
    myBuildType = buildType;
    myBuildFeatureId = buildFeatureId;
  }

  public boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) {
    return false;
  }

  public boolean buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
    return false;
  }

  public boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    return false;
  }

  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    return false;
  }

  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) {
    return false;
  }

  public boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    return false;
  }

  public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
    return false;
  }

  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) {
    return false;
  }

  protected int getConnectionTimeout() {
    return myConnectionTimeout;
  }

  public void setConnectionTimeout(int timeout) {
    myConnectionTimeout = timeout;
  }

  @Nullable
  public String getVcsRootId() {
    return myParams.get(Constants.VCS_ROOT_ID_PARAM);
  }

  @NotNull
  public SBuildType getBuildType() { return myBuildType; }

  @NotNull
  public String getBuildFeatureId() { return myBuildFeatureId; }

  public CommitStatusPublisherProblems getProblems() {return myProblems; }
}
