package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class BaseCommitStatusPublisher implements CommitStatusPublisher {

  protected final Map<String, String> myParams;

  protected BaseCommitStatusPublisher(@NotNull Map<String, String> params) {
    myParams = params;
  }

  public void buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) {
  }

  public void buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
  }

  public void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
  }

  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
  }

  public void buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @NotNull User user, @NotNull String comment, boolean buildInProgress) {
  }

  public void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
  }

  public void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
  }

  public void buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision) {
  }

  @Nullable
  public String getVcsRootId() {
    return myParams.get(Constants.VCS_ROOT_ID_PARAM);
  }
}
