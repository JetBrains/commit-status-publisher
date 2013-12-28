package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;

public interface CommitStatusPublisher {

  void buildStarted(@NotNull SRunningBuild build);

  void buildFinished(@NotNull SFinishedBuild build);

}
