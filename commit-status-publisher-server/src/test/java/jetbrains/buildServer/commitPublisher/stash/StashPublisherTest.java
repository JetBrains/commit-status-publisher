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

package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.MockBuildPromotion;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.DefaultStatusMessages;
import jetbrains.buildServer.commitPublisher.MockQueuedBuild;
import jetbrains.buildServer.commitPublisher.stash.data.JsonStashBuildStatus;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class StashPublisherTest extends BaseStashPublisherTest {

  public StashPublisherTest() {
    myServerVersion = "6.0";
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*%s.*", REVISION, DefaultStatusMessages.BUILD_QUEUED));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*%s by %s.*%s.*", REVISION, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*%s.*", REVISION, DefaultStatusMessages.BUILD_STARTED));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Running with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*with a comment by %s.*%s.*", REVISION, PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*%s.*", REVISION, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL));
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*Failure.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*api/.*/owner/repos/project.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/rest");
    setExpectedEndpointPrefix("/build-status/1.0/commits");
    super.setUp();
  }

  public void shoudld_calculate_correct_revision_status() {
    BuildPromotion promotion = new MockBuildPromotion();
    StashPublisher publisher = (StashPublisher)myPublisher;
    assertNull(publisher.getRevisionStatus(promotion, (JsonStashBuildStatus)null));
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, null, null, null, null, null, null, null, null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, null, null, null, null, null, null, StashBuildStatus.FAILED.name(), null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, null, null, null, null, null, null, StashBuildStatus.SUCCESSFUL.name(), null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, null, null, null, null, null, null, "nonsense", null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL, null, null, null, null, null, StashBuildStatus.INPROGRESS.name(), null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, null, null, null, null, null, null, StashBuildStatus.INPROGRESS.name(), null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_STARTED, null, null, null, null, null, StashBuildStatus.INPROGRESS.name(), null, null)).getTriggeredEvent());
    assertNull(publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, "nonsense", null, null, null, null, null, StashBuildStatus.INPROGRESS.name(), null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.QUEUED, publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_QUEUED, null, null, null, null, null, StashBuildStatus.INPROGRESS.name(), null, null)).getTriggeredEvent());
    assertEquals(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE, publisher.getRevisionStatus(promotion, new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE, null, null, null, null, null, StashBuildStatus.INPROGRESS.name(), null, null)).getTriggeredEvent());
  }

  public void should_define_correctly_if_event_allowed() {
    MockQueuedBuild removedBuild = new MockQueuedBuild();
    removedBuild.setBuildTypeId("buildType");
    removedBuild.setItemId("123");
    StashPublisher publisher = (StashPublisher)myPublisher;
    assertTrue(publisher.getRevisionStatusForRemovedBuild(removedBuild, new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_QUEUED, null, null, null, null, "http://localhost/viewQueued.html?itemId=123", StashBuildStatus.INPROGRESS.name(), null, null)).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
    assertFalse(publisher.getRevisionStatusForRemovedBuild(removedBuild, new JsonStashBuildStatus(null, DefaultStatusMessages.BUILD_QUEUED, null, null, null, null, "http://localhost/viewQueued.html?itemId=321", StashBuildStatus.INPROGRESS.name(), null, null)).isEventAllowed(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE));
  }
}