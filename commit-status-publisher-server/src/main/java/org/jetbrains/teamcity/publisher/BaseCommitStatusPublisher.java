package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class BaseCommitStatusPublisher implements CommitStatusPublisher {

  protected final Map<String, String> myParams;

  protected BaseCommitStatusPublisher(@NotNull Map<String, String> params) {
    myParams = params;
  }

  public void buildStarted(@NotNull SRunningBuild build) {
  }

  public void buildFinished(@NotNull SFinishedBuild build) {
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
