package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class BaseCommitStatusPublisher implements CommitStatusPublisher {

  protected final Map<String, String> myParams;

  protected BaseCommitStatusPublisher(@NotNull Map<String, String> params) {
    myParams = params;
  }

  public void buildQueued(@NotNull SQueuedBuild build) {
  }

  public void buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull User user, String comment) {
  }

  public void buildStarted(@NotNull SRunningBuild build) {
  }

  public void buildInterrupted(@NotNull SRunningBuild build) {
  }

  public void buildChangedStatus(@NotNull SRunningBuild build, Status oldStatus, Status newStatus) {
  }

  public void buildFinished(@NotNull SFinishedBuild build) {
  }

  public void buildCommented(@NotNull SBuild build, @NotNull User user, @NotNull String comment) {
  }

  @Nullable
  protected BuildRevision getBuildRevisionForVote(@NotNull SBuild build) {
    String vcsRootId = getVcsRootId();
    if (vcsRootId == null)
      return null;
    long parentRootId = Long.valueOf(vcsRootId);
    for (BuildRevision revision : build.getRevisions()) {
      if (parentRootId == revision.getRoot().getParentId())
        return revision;
    }
    return null;
  }

  protected String getVcsRootId() {
    return myParams.get("vcsRootId");
  }
}
