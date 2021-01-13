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

package jetbrains.buildServer.commitPublisher.space;

import org.jetbrains.annotations.NotNull;

public class Constants {

  public static final String SPACE_PUBLISHER_ID = "spaceStatusPublisher";
  public static final String SPACE_SERVER_URL = "spaceServerUrl";
  public static final String SPACE_PROJECT_KEY = "spaceProjectKey";

  public static final String SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME = "spaceCommitStatusPublisherDisplayName";

  public static final String SPACE_CONNECTION_ID = "spaceConnectionId";
  public static final String SPACE_CREDENTIALS_TYPE = "spaceCredentialsType";
  public static final String SPACE_CREDENTIALS_CONNECTION = "spaceCredentialsConnection";

  public static final String SPACE_DEFAULT_DISPLAY_NAME = "TeamCity";

  @NotNull
  public String getSpacePublisherId() {
    return SPACE_PUBLISHER_ID;
  }

  @NotNull
  public String getSpaceServerUrl() {
    return SPACE_SERVER_URL;
  }

  @NotNull
  public String getSpaceProjectKey() {
    return SPACE_PROJECT_KEY;
  }

  @NotNull
  public String getSpaceCommitStatusPublisherDisplayName() {
    return SPACE_COMMIT_STATUS_PUBLISHER_DISPLAY_NAME;
  }

  @NotNull
  public String getSpaceConnectionId() {
    return SPACE_CONNECTION_ID;
  }

  @NotNull
  public String getSpaceCredentialsType() {
    return SPACE_CREDENTIALS_TYPE;
  }

  @NotNull
  public String getSpaceCredentialsConnection() {
    return SPACE_CREDENTIALS_CONNECTION;
  }

}
