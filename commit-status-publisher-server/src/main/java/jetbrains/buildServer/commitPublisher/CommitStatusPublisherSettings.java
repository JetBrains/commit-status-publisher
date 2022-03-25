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

package jetbrains.buildServer.commitPublisher;

import java.security.KeyStore;
import jetbrains.buildServer.TeamCityExtension;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

import java.util.Map;

public interface CommitStatusPublisherSettings extends TeamCityExtension {

  @NotNull
  String getId();

  @NotNull
  String getName();

  @Nullable
  String getEditSettingsUrl();

  @Nullable
  Map<String, String> getDefaultParameters();

  /**
   * Transforms parameters of the publisher before they are shown in UI
   * @param params parameters to transform
   * @return map of transformed parameters or null if no transformation is needed
   */
  @Nullable
  Map<String, String> transformParameters(@NotNull Map<String, String> params);

  @Nullable
  CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params);

  @NotNull
  String describeParameters(@NotNull Map<String, String> params);

  @Nullable
  PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate);

  @NotNull
  Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(@NotNull final SProject project, @NotNull final SUser user);

  boolean isEnabled();

  boolean isPublishingForVcsRoot(VcsRoot vcsRoot);

  public boolean isEventSupported(Event event, final SBuildType buildType, final Map<String, String> params);

  boolean isTestConnectionSupported();

  default boolean isFQDNTeamCityUrlRequired() { return false; }

  void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException;

  @Nullable
  default public String getServerVersion(@NotNull String url) {
    return null;
  }

  @Nullable
  KeyStore trustStore();

  @Nullable
  default Map<String, Object> checkHealth(@NotNull SBuildType buildType, @NotNull Map<String, String> params) {
   return null;
  }
}
