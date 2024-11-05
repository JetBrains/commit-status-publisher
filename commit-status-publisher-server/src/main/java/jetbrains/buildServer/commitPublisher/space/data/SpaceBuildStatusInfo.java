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

package jetbrains.buildServer.commitPublisher.space.data;

/**
 * ExternalCheckDTO
 */
public class SpaceBuildStatusInfo {
  public final String executionStatus;
  public final String description;
  public final Long timestamp;
  public final String taskId;
  public final String taskName;
  public final String url;
  public final String externalServiceName;
  public final String taskBuildId;

  public SpaceBuildStatusInfo(String executionStatus,
                              String description,
                              Long timestamp,
                              String taskName,
                              String url,
                              String taskId,
                              String externalServiceName,
                              String taskBuildId) {
    this.executionStatus = executionStatus;
    this.description = description;
    this.timestamp = timestamp;
    this.taskName = taskName;
    this.url = url;
    this.taskId = taskId;
    this.externalServiceName = externalServiceName;
    this.taskBuildId = taskBuildId;
  }
}
