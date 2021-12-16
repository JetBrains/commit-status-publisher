package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG_CATEGORY;

public class RevisionStatus {
  public static final Logger LOG = Logger.getInstance(LOG_CATEGORY);

  private final CommitStatusPublisher.Event myTriggeredEvent;
  private final String myDescription;
  private final boolean myIsLastStatusForRevision;

  public RevisionStatus(@Nullable CommitStatusPublisher.Event triggeredEvent, @Nullable String description, boolean isLastStatusForRevision) {
    myTriggeredEvent = triggeredEvent;
    myDescription = description;
    myIsLastStatusForRevision = isLastStatusForRevision;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public boolean isEventAllowed(@NotNull CommitStatusPublisher.Event event) {
    if (myTriggeredEvent == null) {
      return true;
    }
    switch (event) {
      case QUEUED:
        return event == myTriggeredEvent || CommitStatusPublisher.Event.REMOVED_FROM_QUEUE == myTriggeredEvent;
      case REMOVED_FROM_QUEUE:
        return myIsLastStatusForRevision && CommitStatusPublisher.Event.QUEUED == myTriggeredEvent;
      case STARTED:
        return CommitStatusPublisher.Event.QUEUED == myTriggeredEvent;
      case FINISHED:
      case INTERRUPTED:
      case FAILURE_DETECTED:
        return true;
      case COMMENTED:
      case MARKED_AS_SUCCESSFUL:
        break;
      default:
        LOG.info("Unknown Comit Status Publisher event received: \"" + event + "\". It will be allowed to be processed");
    }
    return true;
  }
}
