

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

package jetbrains.buildServer.commitPublisher.gerrit;

import com.jcraft.jsch.JSchException;
import java.io.IOException;
import jetbrains.buildServer.commitPublisher.PublisherException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface does not declare full gerrit functionality, but only the methods required
 * by Commit Status Publisher.
 */
interface GerritClient {

  void review(@NotNull GerritConnectionDetails connectionDetails, @Nullable final String label, @NotNull String vote, @NotNull String message, @NotNull String revision) throws Exception;

  void testConnection(@NotNull GerritConnectionDetails connectionDetails) throws JSchException, IOException, PublisherException;

  String runCommand(@NotNull GerritConnectionDetails connectionDetails, @NotNull String command) throws JSchException, IOException;

}