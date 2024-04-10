

package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitStatusPublisher {

  boolean isEventSupported(Event event);

  boolean isAvailable(@NotNull BuildPromotion buildPromotion);

  boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException;

  boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException;

  boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) throws PublisherException;

  boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException;

  boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException;

  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException;

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException;

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

  /**
   * Returns the <em>(VCS specific)</em> fallback revisions to be used if no {@link BuildRevision}s could be determined.
   * <p>
   * <em>Note:</em> Publishing statuses to an unknown / fallback revisions only makes sense in very limited use cases.
   * Currently only {@link jetbrains.buildServer.commitPublisher.space.SpacePublisher} supports this.
   * </p>
   *
   * @param build the current build, if any
   * @return empty collection if this publisher doesn't support fallback revisions
   */
  @NotNull
  Collection<BuildRevision> getFallbackRevisions(@Nullable SBuild build);

  /**
   * Whether this publisher was created from a build feature or not.
   * If the publisher settings support the creation of publishers without a build feature, and this particular
   * publisher instance was created in such a way, this method will return {@code false}.
   *
   * @return true by default, false if no build feature exists for this publisher
   * @see CommitStatusPublisherSettings#isFeatureLessPublishingSupported(SBuildType)
   * @since 2024.07
   */
  boolean hasBuildFeature();

  enum Event {
    STARTED("buildStarted", EventPriority.FIRST, true), FINISHED("buildFinished", true),
    QUEUED("buildQueued", EventPriority.FIRST, true), REMOVED_FROM_QUEUE("buildRemovedFromQueue", EventPriority.FIRST, false),
    COMMENTED("buildCommented", EventPriority.ANY, true), INTERRUPTED("buildInterrupted", true),
    FAILURE_DETECTED("buildFailureDetected", true), MARKED_AS_SUCCESSFUL("buildMarkedAsSuccessful", true);

    private final static String PUBLISHING_TASK_PREFIX = "publishBuildStatus";

    private final String myName;
    private final EventPriority myEventPriority;
    private final boolean myShouldRetry;

    Event(String name, boolean shouldRetry) {
      this(name, EventPriority.CONSEQUENT, shouldRetry);
    }

    Event(String name, EventPriority eventPriority, boolean shouldRetry) {
      myName = PUBLISHING_TASK_PREFIX + "." + name;
      myEventPriority = eventPriority;
      myShouldRetry = shouldRetry;
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

    public boolean isRetryable() {
      return myShouldRetry;
    }

    /**
     * @return true if this event can override status from newer build with status of older build
     */
    public boolean canOverrideStatus() {
      return this == COMMENTED || this == MARKED_AS_SUCCESSFUL;
    }

    private enum EventPriority {
      FIRST, // the event of this priority will not be accepted if any of the previous events are of the type CONSEQUENT
      ANY, // accepted at any time, will not prevent any events to be accepted after it
      CONSEQUENT // accepted at any time too, but will prevent events of priority FIRST to be accepted after it
    }
  }
}