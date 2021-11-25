package jetbrains.buildServer.commitPublisher;

import java.util.Date;
import org.jetbrains.annotations.Nullable;

public class RevisionStatus {
  private final CommitStatusPublisher.Event myTriggeredEvent;
  private final String myDescription;
  private final Date myUpdated;

  public RevisionStatus(@Nullable CommitStatusPublisher.Event triggeredEvent, @Nullable Date updated, @Nullable String description) {
    myTriggeredEvent = triggeredEvent;
    myDescription = description;
    myUpdated = updated;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  @Nullable
  public Date getUpdated() {
    return myUpdated;
  }

  public boolean isEventAllowed(CommitStatusPublisher.Event event) {
    if (myTriggeredEvent == null) {
      return false;
    }
    return myTriggeredEvent.isConsequentTask() && event.isConsequentTask() || myTriggeredEvent.isFirstTask() && event.isFirstTask();
  }
}
