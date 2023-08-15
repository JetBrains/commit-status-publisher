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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.http.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 13/09/16.
 */
class MockPublisher extends BaseCommitStatusPublisher implements CommitStatusPublisher {

  static final String PUBLISHER_ERROR = "Simulated publisher exception";

  private final WebLinks myLinks;
  private final String myType;
  private String myVcsRootId = null;

  private User myLastUser = null;

  private boolean myShouldThrowException = false;
  private boolean myShouldReportError = false;
  private int myFailuresReceived = 0;
  private int mySuccessReceived = 0;
  private final Set<Event> myEventsToWait = new HashSet<Event>();
  private int myShouldFailToPublish = 0;

  private final PublisherLogger myLogger;

  private final LinkedList<HttpMethod> myHttpRequests = new LinkedList<>();
  private final Map<String, Map<String, LinkedList<MockStatus>>> myMockState = new ConcurrentHashMap<>(); // { revision : { buildTypeId : [ status ] }}
  private final AtomicInteger myIdGenerator = new AtomicInteger(0);

  boolean isFailureReceived() { return myFailuresReceived > 0; }
  boolean isSuccessReceived() { return mySuccessReceived > 0; }

  String getLastComment() {
    MockState lastStatus = getLastStatus();
    return lastStatus == null ? null : lastStatus.myStatus.myComment;
  }

  List<String> getCommentsReceived() {
    return myMockState.values().stream()
                      .map(btToStatuses -> btToStatuses.values())
                      .flatMap(Collection::stream)
                      .flatMap(Collection::stream)
                      .sorted()
                      .map(status -> status.myComment)
                      .collect(Collectors.toList());
  }

  String getLastTargetRevision() {
    MockState lastStatus = getLastStatus();
    return lastStatus == null ? null : lastStatus.myRevision;
  }

  List<HttpMethod> getSentRequests() {
    return new ArrayList<>(myHttpRequests);
  }

  List<String> getPublishingTargetRevisions() {
    return new ArrayList<>(myMockState.keySet());
  }

  User getLastUser() { return myLastUser; }
  List<Event> getEventsReceived() {
    return myMockState.values().stream()
      .map(btToStatuses -> btToStatuses.values())
      .flatMap(Collection::stream)
      .flatMap(Collection::stream)
      .sorted()
      .map(status -> status.myEvent)
      .collect(Collectors.toList());
  }

  MockPublisher(@NotNull CommitStatusPublisherSettings settings,
                @NotNull String publisherType,
                @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                @NotNull Map<String, String> params,
                @NotNull CommitStatusPublisherProblems problems,
                @NotNull PublisherLogger logger,
                @NotNull WebLinks links) {
    super(settings, buildType, buildFeatureId, params, problems);
    myLogger = logger;
    myType = publisherType;
    myLinks = links;
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

  void shouldFailToPublish(int cntFailures) {
    myShouldFailToPublish = cntFailures;
  }

  private void checkShouldFailToPublish() throws PublisherException {
    if (myShouldFailToPublish > 0) {
      myShouldFailToPublish -= 1;
      throw new PublisherException("Network failure").setShouldRetry();
    }
  }

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
  }

  @Override
  protected WebLinks getLinks() {
    return myLinks;
  }

  @Override
  public boolean buildQueued(@NotNull final BuildPromotion buildPromotion,
                             @NotNull final BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    SBuildType buildType = buildPromotion.getBuildType();
    pretendToHandleEvent(Event.QUEUED);
    myHttpRequests.add(HttpMethod.POST);
    checkShouldFailToPublish();
    saveStatus(revision, buildType, new MockStatus(Event.QUEUED, additionalTaskInfo.getComment()));
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull final BuildPromotion buildPromotion, @NotNull final BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo)
    throws PublisherException {
    myLastUser = additionalTaskInfo.getCommentAuthor();
    myHttpRequests.add(HttpMethod.POST);
    checkShouldFailToPublish();
    saveStatus(revision, buildPromotion.getBuildType(), new MockStatus(Event.REMOVED_FROM_QUEUE, additionalTaskInfo.getComment()));
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull final SBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.STARTED);
    myHttpRequests.add(HttpMethod.POST);
    checkShouldFailToPublish();
    saveStatus(revision, build.getBuildType(), new MockStatus(Event.STARTED, DefaultStatusMessages.BUILD_STARTED));
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.FINISHED);
    myHttpRequests.add(HttpMethod.POST);
    Status s = build.getBuildStatus();
    if (s.equals(Status.NORMAL)) mySuccessReceived++;
    if (s.equals(Status.FAILURE)) myFailuresReceived++;
    if (myShouldThrowException) {
      throw new PublisherException(PUBLISHER_ERROR);
    } else if (myShouldReportError) {
      myProblems.reportProblem(this, "My build", null, null, myLogger);
    }
    checkShouldFailToPublish();
    saveStatus(revision, build.getBuildType(), new MockStatus(Event.FINISHED, DefaultStatusMessages.BUILD_FINISHED));
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
    myHttpRequests.add(HttpMethod.POST);
    checkShouldFailToPublish();
    saveStatus(revision, build.getBuildType(), new MockStatus(Event.COMMENTED, comment));
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull final SBuild build, @NotNull final BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.INTERRUPTED);
    myHttpRequests.add(HttpMethod.POST);
    checkShouldFailToPublish();
    saveStatus(revision, build.getBuildType(), new MockStatus(Event.INTERRUPTED, null));
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    pretendToHandleEvent(Event.FAILURE_DETECTED);
    myHttpRequests.add(HttpMethod.POST);
    myFailuresReceived++;
    checkShouldFailToPublish();
    saveStatus(revision, build.getBuildType(), new MockStatus(Event.FAILURE_DETECTED, null));
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    pretendToHandleEvent(Event.MARKED_AS_SUCCESSFUL);
    myHttpRequests.add(HttpMethod.POST);
    checkShouldFailToPublish();
    saveStatus(revision, build.getBuildType(), new MockStatus(Event.MARKED_AS_SUCCESSFUL, null));
    return super.buildMarkedAsSuccessful(build, revision, buildInProgress);
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    myHttpRequests.add(HttpMethod.GET);
    checkShouldFailToPublish();
    MockState lastStatus = getLastStatus(revision);
    if (lastStatus == null) {
      return null;
    }
    boolean isSameBuildType = lastStatus.myBuildTypeId.equals(buildPromotion.getBuildType().getBuildTypeId());
    return new RevisionStatus(lastStatus.myStatus.myEvent, lastStatus.myStatus.myComment, isSameBuildType);
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild,
                                                         @NotNull BuildRevision revision) throws PublisherException {
    return getRevisionStatus(removedBuild.getBuildPromotion(), revision);
  }
  
  private void saveStatus(BuildRevision revision, BuildType buildType, MockStatus status) {
    myMockState.computeIfAbsent(revision.getRevision(), k -> new HashMap<>())
               .computeIfAbsent(buildType != null ? buildType.getBuildTypeId() : "unknown", k -> new LinkedList<>())
               .add(status);
  }

  private MockState getLastStatus(BuildRevision revision, BuildType buildType) {
    MockStatus lastStatus = myMockState.getOrDefault(revision.getRevision(), Collections.emptyMap()).get(buildType.getBuildTypeId()).getLast();
    return new MockState(lastStatus, revision.getRevision(), buildType.getBuildTypeId());
  }

  private MockState getLastStatus(BuildRevision revision) {
    Map<String, LinkedList<MockStatus>> btsToStatuses = myMockState.getOrDefault(revision.getRevision(), Collections.emptyMap());
    MockStatus result = null;
    String buildTypeId = null;
    for (Map.Entry<String, LinkedList<MockStatus>> btToStatuses : btsToStatuses.entrySet()) {
      String curBuildTypeId = btToStatuses.getKey();
      MockStatus lastForBuildType = btToStatuses.getValue().getLast();
      if (result == null || result.myId < lastForBuildType.myId) {
        result = lastForBuildType;
        buildTypeId = curBuildTypeId;
      }
    }
    return (result != null & buildTypeId != null) ? new MockState(result, revision.getRevision(), buildTypeId) : null;
  }

  private MockState getLastStatus() {
    MockStatus result = null;
    String revision = null;
    String buildTypeId = null;
    for (Map.Entry<String, Map<String, LinkedList<MockStatus>>> revToBuildTypes : myMockState.entrySet()) {
      String curRevision = revToBuildTypes.getKey();
      for (Map.Entry<String, LinkedList<MockStatus>> btToStatuses : revToBuildTypes.getValue().entrySet()) {
        String curBuildTypeId = btToStatuses.getKey();
        MockStatus lastForBuildType = btToStatuses.getValue().getLast();
        if (result == null || result.myId < lastForBuildType.myId) {
          result = lastForBuildType;
          revision = curRevision;
          buildTypeId = curBuildTypeId;
        }
      }
    }
    return (result != null & revision != null & buildTypeId != null) ? new MockState(result, revision, buildTypeId) : null;
  }

  private class MockStatus implements Comparable {
    final Event myEvent;
    final String myComment;
    final int myId;

    public MockStatus(@NotNull Event event, @Nullable String comment) {
      myEvent = event;
      myComment = comment;
      myId = myIdGenerator.getAndIncrement();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MockStatus that = (MockStatus)o;
      return myId == that.myId && myEvent == that.myEvent && Objects.equals(myComment, that.myComment);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myEvent, myComment, myId);
    }


    @Override
    public int compareTo(@NotNull Object o) {
      if (this == o) return 0;
      if (getClass() != o.getClass()) return 1;
      return Integer.compare(myId, ((MockStatus)o).myId);
    }
  }

  class MockState {
    final MockStatus myStatus;
    final String myRevision;
    final String myBuildTypeId;

    public MockState(@NotNull MockStatus status, @NotNull String revision, @NotNull String buildTypeId) {
      myStatus = status;
      myRevision = revision;
      myBuildTypeId = buildTypeId;
    }
  }
}
