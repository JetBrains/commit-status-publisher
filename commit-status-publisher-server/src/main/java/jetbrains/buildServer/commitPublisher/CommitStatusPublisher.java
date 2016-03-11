package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitStatusPublisher {

  void buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision);

  void buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment);

  void buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision);

  void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision);

  void buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress);

  void buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision);

  void buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision);

  void buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision);

  @Nullable
  String getVcsRootId();

  @NotNull
  String toString();

  String getId();
}
