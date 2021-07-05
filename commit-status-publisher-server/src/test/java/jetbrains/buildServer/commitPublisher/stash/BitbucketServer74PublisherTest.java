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
public class BitbucketServer74PublisherTest extends BaseStashPublisherTest {

  public BitbucketServer74PublisherTest() {
    myServerVersion = "7.4";
    myExpectedRegExps.put(EventToTest.QUEUED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Build queued.*INPROGRESS.*", REVISION));
    myExpectedRegExps.put(EventToTest.REMOVED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Build removed from queue.*FAILED.*", REVISION));
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Build started.*INPROGRESS.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Success.*SUCCESSFUL.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Failure.*FAILED.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Success with a comment by %s.*%s.*SUCCESSFUL.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Failure with a comment by %s.*%s.*FAILED.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Running with a comment by %s.*%s.*INPROGRESS.*", REVISION, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*%s.*with a comment by %s.*%s.*FAILED.*", REVISION, PROBLEM_DESCR, USER.toLowerCase(), COMMENT));
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*%s.*FAILED.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*%s.*FAILED.*", REVISION, PROBLEM_DESCR));
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*marked as successful.*SUCCESSFUL.*", REVISION));
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Build marked as successful.*INPROGRESS.*", REVISION));
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*projects/owner/repos/project/commits/%s.*ENTITY:.*Failure.*%s.*FAILED.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, ".*api/.*/owner/repos/project.*");
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    setExpectedApiPath("/rest");
    setExpectedEndpointPrefix("/api/1.0/projects");
    super.setUp();
  }
}