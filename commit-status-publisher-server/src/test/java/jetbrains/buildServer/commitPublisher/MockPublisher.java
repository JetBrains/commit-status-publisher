/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.*;
import java.util.stream.Collectors;
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

  private User myLastUser = null;

  private boolean myShouldThrowException = false;
  private boolean myShouldReportError = false;
  private int myFailuresReceived = 0;
  private int mySuccessReceived = 0;
  private final Set<Event> myEventsToWait = new HashSet<Event>();

  private final PublisherLogger myLogger;

  private final LinkedList<Event> myEventsReceived = new LinkedList<>();
  private final LinkedList<String> myPublishingBuilds = new LinkedList<>();
  private final LinkedList<String> myCommentsReceived = new LinkedList<>();

  boolean isFailureReceived() { return myFailuresReceived > 0; }
  boolean isSuccessReceived() { return mySuccessReceived > 0; }

  String getLastComment() {
    if (myCommentsReceived.isEmpty()) {
      return null;
    }
    return myCommentsReceived.getLast();
  }

  List<String> getCommentsReceived() {
    return new ArrayList<>(myCommentsReceived);
  }

  User getLastUser() { return myLastUser; }
  List<Event> getEventsReceived() { return myEventsReceived.stream().filter(Objects::nonNull).collect(Collectors.toList()); }

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
  public boolean buildQueued(@NotNull final BuildPromotion buildPromotion,
                             @NotNull final BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    pretendToHandleEvent(Event.QUEUED);
    myCommentsReceived.add(additionalTaskInfo.compileQueueRelatedMessage());
    myPublishingBuilds.add(prepareContextName(buildPromotion.getBuildType()));
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull final BuildPromotion buildPromotion, @NotNull final BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) {
    myCommentsReceived.add(additionalTaskInfo.compileQueueRelatedMessage());
    myPublishingBuilds.add(prepareContextName(buildPromotion.getBuildType()));
    myEventsReceived.add(null);
    myLastUser = additionalTaskInfo.getCommentAuthor();
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull final SBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.STARTED);
    myCommentsReceived.add(DefaultStatusMessages.BUILD_STARTED);
    myPublishingBuilds.add(prepareContextName(build.getBuildType()));
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.FINISHED);
    myCommentsReceived.add(DefaultStatusMessages.BUILD_FINISHED);
    myPublishingBuilds.add(prepareContextName(build.getBuildType()));
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
    myCommentsReceived.add(comment);
    myPublishingBuilds.add(prepareContextName(build.getBuildType()));
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

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) {
    if (myEventsReceived.isEmpty()) {
      return null;
    }
    String buildContext = prepareContextName(buildPromotion.getBuildType());
    boolean isLastForRevision = buildContext.equals(myPublishingBuilds.getLast());
    Event last = myEventsReceived.getLast();
    if (last != Event.QUEUED && last != Event.REMOVED_FROM_QUEUE && last != Event.STARTED && last != null) {  // only checked events are important for futher processing
      last = null;
    }
    return new RevisionStatus(last, isLastForRevision);
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild,
                                                         @NotNull BuildRevision revision) throws PublisherException {
    return getRevisionStatus(removedBuild.getBuildPromotion(), revision);
  }

  @Override
  public CommonBuildStatus getLatestInformativeBuildStatusForPromotion(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    if (myEventsReceived == null) {
      return null;
    }
    for (int i = myEventsReceived.size() - 1; i >= 0; i--) {
      Event event = myEventsReceived.get(i);
      if (event == Event.REMOVED_FROM_QUEUE || event == null) continue;
      String context = myPublishingBuilds.get(i);
      String contextName = prepareContextName(buildPromotion.getBuildType());
      if (context.equals(contextName) && event == Event.QUEUED) continue;
      String comment = myCommentsReceived.get(i);
      return new CommonBuildStatus(contextName, event.name(), comment, "url");
    }
    return null;
  }

  @Override
  public boolean publish(BuildRevision revision, CommonBuildStatus status) throws PublisherException {
    Event event = Event.valueOf(status.getState());
    myEventsReceived.add(event);
    myCommentsReceived.add(status.getDescription());
    myPublishingBuilds.add(status.getBuild());
    return true;
  }

  private String prepareContextName(SBuildType buildType) {
    return String.format("%s (%s)", buildType.getName(), buildType.getProject().getName());
  }
}
