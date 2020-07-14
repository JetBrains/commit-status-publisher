package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitStatusPublisher {

  boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) throws PublisherException;

  boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) throws PublisherException;

  boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException;

  @NotNull
  String getBuildFeatureId();

  @NotNull
  SBuildType getBuildType();

  @Nullable
  String getVcsRootId();

  @NotNull
  String toString();

  @NotNull
  String getId();

  @NotNull
  CommitStatusPublisherSettings getSettings();

  boolean isPublishingForRevision(@NotNull BuildRevision revision);

  void setConnectionTimeout(int timeout);

  boolean isEventSupported(Event event);


  enum Event {
    STARTED("buildStarted", EventPriority.FIRST), FINISHED("buildFinished"),
    QUEUED("buildQueued", EventPriority.FIRST), REMOVED_FROM_QUEUE("buildRemovedFromQueue", EventPriority.FIRST),
    COMMENTED("buildCommented", EventPriority.ANY), INTERRUPTED("buildInterrupted"),
    FAILURE_DETECTED("buildFailureDetected"), MARKED_AS_SUCCESSFUL("buildMarkedAsSuccessful");

    private final static String PUBLISHING_TASK_PREFIX = "publishBuildStatus";

    private final String myName;
    private final EventPriority myEventPriority;

    Event(String name) {
      this(name, EventPriority.CONSEQUENT);
    }

    Event(String name, EventPriority eventPriority) {
      myName = PUBLISHING_TASK_PREFIX + "." + name;
      myEventPriority = eventPriority;
    }

    public String getName() {
      return myName;
    }

    public boolean isFirstTask() {
      return myEventPriority == EventPriority.FIRST;
    }

    public boolean isConsequentTask() {
      return myEventPriority == EventPriority.CONSEQUENT;
    }

    private enum EventPriority {
      FIRST, // the event of this priority will not be accepted if any of the previous events are of the type CONSEQUENT
      ANY, // accepted at any time, will not prevent any events to be accepted after it
      CONSEQUENT // accepted at any time too, but will prevent events of priority FIRST to be accepted after it
    }
  }
}
