package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;

public interface CommitStatusPublisher {

  void buildStarted(@NotNull SRunningBuild build);

  void buildFinished(@NotNull SFinishedBuild build);

  public void buildCommented(@NotNull SBuild build, @NotNull User user, @NotNull String comment);
}
