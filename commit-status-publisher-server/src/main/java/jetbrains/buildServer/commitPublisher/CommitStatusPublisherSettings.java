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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.TeamCityExtension;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Creates a publisher instance for the given VCS root, without the need for a build feature, if supported.
   *
   * @param buildType the build configuration
   * @param vcsRoot   VCS root
   * @return created publisher or null
   */
  @Nullable
  CommitStatusPublisher createFeaturelessPublisher(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot);

  @NotNull
  String describeParameters(@NotNull Map<String, String> params);

  @Nullable
  PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate);

  @NotNull
  List<OAuthConnectionDescriptor> getOAuthConnections(@NotNull final SProject project, @NotNull final SUser user);

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

  /**
   * Attempts to construct the specific {@link HttpCredentials} with the help of the provided parameters and other available data (e.g. token storage).
   * May return null if operation is not applicable.
   *
   * @param root VCS root if available
   * @param params parameters
   * @return credentials or null
   * @throws PublisherException on configuration errors
   */
  @Nullable
  default HttpCredentials getCredentials(@Nullable VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    return null;
  }

  /**
   * Can be used by implementors to supply specific model attributes for the settings page.
   *
   * @param project owning project
   * @param params  parameters
   * @return Map of attribute name to value
   */
  @NotNull
  default Map<String, Object> getSpecificAttributes(@NotNull SProject project, @NotNull Map<String, String> params) {
    return Collections.emptyMap();
  }

  boolean isFeatureLessPublishingSupported(@NotNull SBuildType buildType);
}
