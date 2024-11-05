

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

package jetbrains.buildServer.commitPublisher.stash.data;

import org.jetbrains.annotations.NotNull;

public class JsonStashBuildStatus {
  public final String buildNumber, description, key, parent, name, ref, url;
  public final String state;
  public final Long duration;
  public final StashTestStatistics testResults;

  public JsonStashBuildStatus(@NotNull DeprecatedJsonStashBuildStatuses.Status status) {
    state = status.state;
    description = status.description;
    key = status.key;
    name = status.name;
    url = status.url;
    buildNumber = null;
    parent = null;
    ref = null;
    duration = null;
    testResults = null;
  }

  public static class StashTestStatistics {
    public int failed, skipped, successful;
  }

  public JsonStashBuildStatus(String buildNumber,
                              String description,
                              String key,
                              String parent,
                              String name,
                              String ref,
                              String url,
                              String state,
                              Long duration,
                              StashTestStatistics testResults) {
    this.buildNumber = buildNumber;
    this.description = description;
    this.key = key;
    this.parent = parent;
    this.name = name;
    this.ref = ref;
    this.url = url;
    this.state = state;
    this.duration = duration;
    this.testResults = testResults;
  }
}