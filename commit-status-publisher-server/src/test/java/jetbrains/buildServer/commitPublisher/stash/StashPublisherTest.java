/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class StashPublisherTest extends BaseStashPublisherTest {

  public StashPublisherTest() {
    myServerVersion = "6.0";
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build queued.*", REVISION));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Build removed from queue.*", REVISION));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build started.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*Success with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*Failure with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Running with a comment by %s.*%s.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*with a comment by %s.*%s.*", REVISION, PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*build-status/.*/commits/%s.*ENTITY:.*FAILED.*%s.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*SUCCESSFUL.*marked as successful.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*build-status/.*/commits/%s.*ENTITY:.*INPROGRESS.*Build marked as successful.*", REVISION));
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
}