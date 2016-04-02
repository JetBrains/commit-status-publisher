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

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.github.GitHubSettings;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by github.com/justmara on 15.03.2016.
 */
public class GitLabSettings extends GitHubSettings implements CommitStatusPublisherSettings {

  public GitLabSettings(@NotNull ChangeStatusUpdater updater) {
    super(updater);
  }

  @NotNull
  public String getId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "GitLab";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return "gitlab/gitlabSettings.jsp";
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    return new GitLabPublisher((jetbrains.buildServer.commitPublisher.gitlab.ChangeStatusUpdater) myUpdater, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Update change status into GitLab";
  }

  public boolean isEnabled() {
    return true;
  }
}
