package jetbrains.buildServer.commitPublisher;

import java.util.*;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 13/09/16.
 */
class MockPublisher extends BaseCommitStatusPublisher implements CommitStatusPublisher {

  static final String PUBLISHER_ERROR = "Simulated publisher exception";

  private final String myType;
  private String myVcsRootId = null;

  private String myLastComment = null;

  private boolean myShouldThrowException = false;
  private boolean myShouldReportError = false;
  private int myFailuresReceived = 0;
  private int mySuccessReceived = 0;
  private Set<Event> myEventsToWait = new HashSet<Event>();

  private final PublisherLogger myLogger;

  private final List<Event> myEventsReceived = new ArrayList<>();

  boolean isFailureReceived() { return myFailuresReceived > 0; }
  boolean isSuccessReceived() { return mySuccessReceived > 0; }

  boolean isFinishedReceived() { return myEventsReceived.contains(Event.FINISHED); }
  boolean isStartedReceived() { return myEventsReceived.contains(Event.STARTED); }
  boolean isCommentedReceived() { return myEventsReceived.contains(Event.COMMENTED); }
  boolean isQueuedReceived() {return myEventsReceived.contains(Event.QUEUED); }
  boolean isRemovedFromQueueReceived() { return myEventsReceived.contains(Event.REMOVED_FROM_QUEUE); }
  boolean isInterruptedReceieved() { return myEventsReceived.contains(Event.INTERRUPTED); }
  String getLastComment() { return myLastComment; }
  List<Event> getEventsReceived() { return myEventsReceived; }

  MockPublisher(@NotNull CommitStatusPublisherSettings settings,
                @NotNull String publisherType,
                @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                @NotNull Map<String, String> params,
                @NotNull CommitStatusPublisherProblems problems,
                @NotNull PublisherLogger logger) {
    super(settings, buildType, buildFeatureId, params, problems);
    myLogger = logger;
    myType = publisherType;
  }

  @Nullable
  @Override
  public String getVcsRootId() {
    return myVcsRootId;
  }

  void setVcsRootId(String vcsRootId) {
    myVcsRootId = vcsRootId;
  }

  void setEventToWait(Event event) {
    myEventsToWait.add(event);
  }

  void notifyWaitingEvent(Event event, long delayMs) throws InterruptedException {
    if (myEventsToWait.contains(event)) {
      Thread.sleep(delayMs);
      synchronized (event) {
        event.notify();
      }
    }
  }

  @NotNull
  @Override
  public String getId() {
    return myType;
  }

  int failuresReceived() { return myFailuresReceived; }

  int finishedReceived() { return (int) myEventsReceived.stream().map(e -> e == Event.FINISHED).count(); }

  int successReceived() { return mySuccessReceived; }

  void shouldThrowException() {myShouldThrowException = true; }

  void shouldReportError() {myShouldReportError = true; }

  private void pretendToHandleEvent(Event event) throws PublisherException {
    if (myEventsToWait.contains(event)) {
      try {
        synchronized(event) {
          event.wait(10000);
        }
      } catch (InterruptedException e) {
        throw new PublisherException("Mock publisher interrupted", e);
      }
    }
    myEventsReceived.add(event);
  }

  @Override
  public boolean buildQueued(@NotNull final SQueuedBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.QUEUED);
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull final SQueuedBuild build, @NotNull final BuildRevision revision, @Nullable final User user, @Nullable final String comment)
    throws PublisherException {
    pretendToHandleEvent(Event.REMOVED_FROM_QUEUE);
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull final SBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.STARTED);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.FINISHED);
    Status s = build.getBuildStatus();
    if (s.equals(Status.NORMAL)) mySuccessReceived++;
    if (s.equals(Status.FAILURE)) myFailuresReceived++;
    if (myShouldThrowException) {
      throw new PublisherException(PUBLISHER_ERROR);
    } else if (myShouldReportError) {
      myProblems.reportProblem(this, "My build", null, null, myLogger);
    }
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull final SBuild build,
                                @NotNull final BuildRevision revision,
                                @Nullable final User user,
                                @Nullable final String comment,
                                final boolean buildInProgress)
    throws PublisherException {
    pretendToHandleEvent(Event.COMMENTED);
    myLastComment = comment;
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull final SBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.INTERRUPTED);
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.FAILURE_DETECTED);
    myFailuresReceived++;
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    pretendToHandleEvent(Event.MARKED_AS_SUCCESSFUL);
    return super.buildMarkedAsSuccessful(build, revision, buildInProgress);
  }
}
