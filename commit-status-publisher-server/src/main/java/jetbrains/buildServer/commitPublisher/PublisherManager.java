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

package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.ExtensionsCollection;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

  @Nullable
  public CommitStatusPublisherSettings findSettings(@NotNull String publisherId) {
    return myPublisherSettings.getExtensions().stream().filter(s -> publisherId.equals(s.getId())).findFirst().orElse(null);
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
}
