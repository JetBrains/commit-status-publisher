/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.github.GitHubPublisher;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Created by github.com/justmara on 15.03.2016.
 */
public class GitLabPublisher extends GitHubPublisher {

  public GitLabPublisher(@NotNull ChangeStatusUpdater updater,
                         @NotNull Map<String, String> params) {
    super(updater, params);
  }

  @Override
  public boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) {
    final jetbrains.buildServer.commitPublisher.github.ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(revision.getRoot(), myParams);
    h.scheduleChangeEnQueued(revision.getRepositoryVersion(), build);
    return true;
  }

  public boolean buildRemovedFromQueue(@NotNull SQueuedBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment) {
    final jetbrains.buildServer.commitPublisher.github.ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(revision.getRoot(), myParams);
    h.scheduleChangeDeQueued(revision.getRepositoryVersion(), build);
    return false;
  }

  @NotNull
  public String toString() {
    return "gitlab";
  }

  @Override
  public String getId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }
}
