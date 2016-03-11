package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitStatusPublisher {

  boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision);

  boolean buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment);

  boolean buildStarted(@NotNull SRunningBuild build, @NotNull BuildRevision revision);

  boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision);

  boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress);

  boolean buildInterrupted(@NotNull SFinishedBuild build, @NotNull BuildRevision revision);

  boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision);

  boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision);

  @Nullable
  String getVcsRootId();

  @NotNull
  String toString();

  String getId();
}
