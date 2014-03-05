package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
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

  public void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
  }

  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
  }

  public void buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @NotNull User user, @NotNull String comment) {
  }

  public void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
  }

  public void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
  }

  @Nullable
  public String getVcsRootId() {
    return myParams.get("vcsRootId");
  }
}
