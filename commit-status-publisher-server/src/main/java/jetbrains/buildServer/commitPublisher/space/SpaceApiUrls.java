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

package jetbrains.buildServer.commitPublisher.space;

import jetbrains.buildServer.commitPublisher.HttpHelper;
import org.jetbrains.annotations.NotNull;

public class SpaceApiUrls {
  static private final String HTTP_API_PART = "api/http";
  static private final String COMMIT_STATUS_PART = "commit-statuses";
  static private final String CHECK_SERVICE_PART = "check-service";

  private static String projectKey(@NotNull String projectKey) {
    return String.format("projects/key:%s", projectKey);
  }

  private static String repository(@NotNull String repository) {
    return String.format("repositories/%s", repository);
  }

  private static String revision(@NotNull String revision) {
    return String.format("revisions/%s", revision);
  }

  static String commitStatusUrl(@NotNull String spaceUrl,
                                @NotNull String projectKey,
                                @NotNull String repository,
                                @NotNull String revision) {
    return String.format("%s/%s/%s/%s/%s/%s",
      HttpHelper.stripTrailingSlash(spaceUrl),
      HTTP_API_PART,
      projectKey(projectKey),
      repository(repository),
      revision(revision),
      COMMIT_STATUS_PART
    );
  }

  static String commitStatusTestConnectionUrl(@NotNull String spaceUrl,
                                              @NotNull String projectKey) {
    return String.format("%s/%s/%s/%s/%s",
      HttpHelper.stripTrailingSlash(spaceUrl),
      HTTP_API_PART,
      projectKey(projectKey),
      COMMIT_STATUS_PART,
      CHECK_SERVICE_PART
    );
  }
}
