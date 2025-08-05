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

public interface DefaultStatusMessages {
  String BUILD_QUEUED = "TeamCity build was queued";
  String BUILD_REMOVED_FROM_QUEUE = "TeamCity build was removed from queue";
  String BUILD_REMOVED_FROM_QUEUE_AS_CANCELED = "TeamCity build was canceled by a failing dependency";
  String BUILD_SKIPPED = "TeamCity build was skipped";
  String BUILD_STARTED = "TeamCity build started";
  String BUILD_FINISHED = "TeamCity build finished";
  String BUILD_FAILED = "TeamCity build failed";
  String BUILD_MARKED_SUCCESSFULL = "TeamCity build was marked as successful";
}
