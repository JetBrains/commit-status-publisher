/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
