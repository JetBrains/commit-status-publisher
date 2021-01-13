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

package jetbrains.buildServer.commitPublisher.gerrit;

import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 09/01/17.
 */
class GerritConnectionDetails {
  private final SProject myProject;
  private final String myGerritProject;
  private final String myServer;
  private final String myUserName;
  private final String myKeyId;

  GerritConnectionDetails(@NotNull SProject project, @NotNull String gerritProject,
                                 @NotNull String server, @NotNull String username, @Nullable String keyId) {
    myProject = project;
    myGerritProject = gerritProject;
    myServer = server;
    myUserName = username;
    myKeyId = keyId;
  }

  @NotNull
  SProject getProject() {
    return myProject;
  }

  @NotNull
  String getGerritProject() {
    return myGerritProject;
  }

  @NotNull
  String getServer() {
    return myServer;
  }

  @NotNull
  String getUserName() {
    return myUserName;
  }

  @Nullable
  String getKeyId() {
    return myKeyId;
  }

}
