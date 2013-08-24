package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;

public interface CommitStatusPublisher {

  void buildStarted(@NotNull SRunningBuild build);

  void buildFinished(@NotNull SRunningBuild build);

}
