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

  public RevisionStatus(@Nullable CommitStatusPublisher.Event triggeredEvent, @Nullable String description, boolean isSameBuildType) {
    myTriggeredEvent = triggeredEvent;
    myDescription = description;
    myIsSameBuildType = isSameBuildType;
  }

  @Nullable
  public CommitStatusPublisher.Event getTriggeredEvent() {
    return myTriggeredEvent;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public boolean isEventAllowed(@NotNull CommitStatusPublisher.Event pendingEvent) {
    if (myTriggeredEvent == null) {
      return true;
    }
    switch (pendingEvent) {
      case QUEUED:
        return myIsSameBuildType && CommitStatusPublisher.Event.QUEUED == myTriggeredEvent;
      case REMOVED_FROM_QUEUE:
        return myIsSameBuildType && CommitStatusPublisher.Event.QUEUED == myTriggeredEvent;
      case STARTED:
      case FINISHED:
      case INTERRUPTED:
      case FAILURE_DETECTED:
      case COMMENTED:
      case MARKED_AS_SUCCESSFUL:
        return true;
      default:
        LOG.info("Unknown Comit Status Publisher event received: \"" + pendingEvent + "\". It will be allowed to be processed");
    }
    return true;
  }
}
