package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;

public interface CommitStatusPublisher {
  void buildQueued(@NotNull SQueuedBuild build);

  void buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull User user, String comment);

  void buildStarted(@NotNull SRunningBuild build);

  void buildInterrupted(@NotNull SRunningBuild build);

  void buildChangedStatus(@NotNull SRunningBuild build, Status oldStatus, Status newStatus);

  void buildFinished(@NotNull SFinishedBuild build);

  void buildCommented(@NotNull SBuild build, @NotNull User user, @NotNull String comment);
}
