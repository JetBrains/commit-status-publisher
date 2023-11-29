package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG_CATEGORY;

public class RevisionStatus {
  public static final Logger LOG = Logger.getInstance(LOG_CATEGORY);

  private final CommitStatusPublisher.Event myTriggeredEvent;
  private final String myDescription;
  private final boolean myIsSameBuildType;
  private final Long myBuildId;
  
  public RevisionStatus(@Nullable CommitStatusPublisher.Event triggeredEvent, @Nullable String description, boolean isSameBuildType, @Nullable Long buildId) {
    myTriggeredEvent = triggeredEvent;
    myDescription = description;
    myIsSameBuildType = isSameBuildType;
    myBuildId = buildId;
  }

  @Nullable
  public CommitStatusPublisher.Event getTriggeredEvent() {
    return myTriggeredEvent;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public boolean isEventAllowed(@NotNull CommitStatusPublisher.Event pendingEvent, long buildId) {
    if (myTriggeredEvent == null) {
      if (pendingEvent.canOverrideStatus()) {
        return myBuildId == null || buildId >= myBuildId; // we don't want to publish status for older build
      } else {
        return true;
      }
    }

    switch (pendingEvent) {
      case QUEUED:
        return myIsSameBuildType && CommitStatusPublisher.Event.QUEUED == myTriggeredEvent;
      case REMOVED_FROM_QUEUE:
        return myIsSameBuildType && CommitStatusPublisher.Event.QUEUED == myTriggeredEvent;
      case COMMENTED:
      case MARKED_AS_SUCCESSFUL:
        return myBuildId == null || buildId >= myBuildId;
      case STARTED:
      case FINISHED:
      case INTERRUPTED:
      case FAILURE_DETECTED:
        return true;
      default:
        LOG.info("Unknown Commit Status Publisher event received: \"" + pendingEvent + "\". It will be allowed to be processed");
    }
    return true;
  }
}
