package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author anton.zamolotskikh, 13/09/16.
 */
class MockPublisher extends BaseCommitStatusPublisher implements CommitStatusPublisher {

  static final String PUBLISHER_ERROR = "Simulated publisher exception";

  private final String myType;
  private String myVcsRootId = null;

  private int myFailuresReceived = 0;
  private int myFinishedReceived = 0;
  private int mySuccessReceived = 0;
  private int myStartedReceived = 0;
  private int myCommentedReceived = 0;
  private String myLastComment = null;

  private boolean myShouldThrowException = false;
  private boolean myShouldReportError = false;
  private final PublisherLogger myLogger;

  boolean isFailureReceived() { return myFailuresReceived > 0; }
  boolean isFinishedReceived() { return myFinishedReceived > 0; }
  boolean isSuccessReceived() { return mySuccessReceived > 0; }
  boolean isStartedReceived() { return myStartedReceived > 0; }
  boolean isCommentedReceived() { return myCommentedReceived > 0; }
  String getLastComment() { return myLastComment; }


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

  @NotNull
  @Override
  public String getId() {
    return myType;
  }

  int failuresReceived() { return myFailuresReceived; }

  int finishedReceived() { return myFinishedReceived; }

  int successReceived() { return mySuccessReceived; }

  void shouldThrowException() {myShouldThrowException = true; }
  void shouldReportError() {myShouldReportError = true; }

  @Override
  public boolean buildStarted(@NotNull final SBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    myStartedReceived++;
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    myFinishedReceived++;
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
    myCommentedReceived++;
    myLastComment = comment;
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) {
    myFailuresReceived++;
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    return super.buildMarkedAsSuccessful(build, revision, buildInProgress);
  }
}
