package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitStatusPublisher {

  void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision);

  void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision);

  void buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @NotNull User user, @NotNull String comment);

  void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision);

  void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision);

  @NotNull
  String getVcsRootId();
}
