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

import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.ExtensionsCollection;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.SVcsRootEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PublisherManager {

  private final ExtensionsCollection<CommitStatusPublisherSettings> myPublisherSettings;

  public PublisherManager(@NotNull ExtensionHolder extensionHolder) {
    myPublisherSettings = extensionHolder.getExtensionsCollection(CommitStatusPublisherSettings.class);
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    String publisherId = params.get(Constants.PUBLISHER_ID_PARAM);
    if (publisherId == null)
      return null;
    CommitStatusPublisherSettings settings = findSettings(publisherId);
    if (settings == null)
      return null;
    return settings.createPublisher(buildType, buildFeatureId, params);
  }

  @NotNull
  public Map<String, CommitStatusPublisher> createConfiguredPublishers(@NotNull SBuildType buildType) {
    final Map<String, CommitStatusPublisher> publishers = new LinkedHashMap<>();
    for (SBuildFeatureDescriptor buildFeatureDescriptor : buildType.getResolvedSettings().getBuildFeatures()) {
      final BuildFeature buildFeature = buildFeatureDescriptor.getBuildFeature();
      if (buildFeature instanceof CommitStatusPublisherFeature) {
        final String featureId = buildFeatureDescriptor.getId();
        final CommitStatusPublisher publisher = createPublisher(buildType, featureId, buildFeatureDescriptor.getParameters());
        if (publisher != null) {
          publishers.put(featureId, publisher);
        }
      }
    }

    return publishers;
  }

  @NotNull
  public Map<String, CommitStatusPublisher> createSupplementaryPublishers(@NotNull SBuildType buildType, @NotNull Map<String, CommitStatusPublisher> existingPublishers) {
    final Set<CommitStatusPublisherSettings> settingsSupportingFeatureless = myPublisherSettings.getExtensions().stream()
                                                                                                .filter(settings -> settings.isEnabled() &&
                                                                                                                    settings.isFeatureLessPublishingSupported(buildType))
                                                                                                .collect(Collectors.toSet());
    if (settingsSupportingFeatureless.isEmpty()) {
      return Collections.emptyMap();
    }

    final Map<String, CommitStatusPublisher> supplementaryPublishers = new HashMap<>();
    final Set<SVcsRoot> notCoveredVcsRoots = findNotCoveredVcsRoots(buildType, existingPublishers.values());

    for (SVcsRoot notCoveredVcsRoot : notCoveredVcsRoots) {
      for (CommitStatusPublisherSettings settings : settingsSupportingFeatureless) {
        final CommitStatusPublisher featurelessPublisher = settings.createFeaturelessPublisher(buildType, notCoveredVcsRoot);
        if (featurelessPublisher != null) {
          supplementaryPublishers.put(featurelessPublisher.getBuildFeatureId(), featurelessPublisher);
          break;
        }
      }
    }

    return supplementaryPublishers;
  }

  @Nullable
  public CommitStatusPublisherSettings findSettings(@NotNull String publisherId) {
    return myPublisherSettings.getExtensions().stream().filter(s -> publisherId.equals(s.getId())).findFirst().orElse(null);
  }

  public boolean isFeatureLessPublishingPossible(@Nullable SBuildType buildType) {
    if (buildType == null) {
      return false;
    }
    for (CommitStatusPublisherSettings settings : myPublisherSettings.getExtensions()) {
      if (settings.isFeatureLessPublishingSupported(buildType)) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  List<CommitStatusPublisherSettings> getAllPublisherSettings() {
    List<CommitStatusPublisherSettings> settings = new ArrayList<CommitStatusPublisherSettings>();
    for (CommitStatusPublisherSettings s : myPublisherSettings.getExtensions()) {
      if (s.isEnabled())
        settings.add(s);
    }
    Collections.sort(settings, new Comparator<CommitStatusPublisherSettings>() {
      public int compare(CommitStatusPublisherSettings o1, CommitStatusPublisherSettings o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return settings;
  }

  @NotNull
  private Set<SVcsRoot> findNotCoveredVcsRoots(@NotNull SBuildType buildType, @NotNull Collection<CommitStatusPublisher> existingPublishers) {
    final Set<String> coveredRootIds = new HashSet<>();
    for (CommitStatusPublisher existingPublisher : existingPublishers) {
      final String vcsRootId = existingPublisher.getVcsRootId();
      if (vcsRootId == null) {
        return Collections.emptySet();
      }

      coveredRootIds.add(vcsRootId);
    }

    return buildType.getVcsRoots().stream()
                    .filter(vcsRoot -> !coveredRootIds.contains(String.valueOf(vcsRoot.getId())) &&
                                       !coveredRootIds.contains(vcsRoot.getExternalId()) &&
                                       !containsAlias(coveredRootIds, vcsRoot))
                    .collect(Collectors.toSet());
  }

  private static boolean containsAlias(@NotNull Set<String> rootIds, @NotNull SVcsRoot vcsRoot) {
    if (vcsRoot instanceof SVcsRootEx) {
      return rootIds.stream().anyMatch(((SVcsRootEx)vcsRoot)::isAliasExternalId);
    }

    return false;
  }
}
