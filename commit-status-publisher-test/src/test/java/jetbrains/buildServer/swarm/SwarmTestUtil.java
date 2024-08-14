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

package jetbrains.buildServer.swarm;

import com.google.common.collect.ImmutableMap;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings.PARAM_URL;

public class SwarmTestUtil {
  private SwarmTestUtil() {
  }

  public static SBuildFeatureDescriptor addSwarmFeature(@NotNull BuildTypeEx buildType, @NotNull String url) {
    return buildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, ImmutableMap.of(
      Constants.PUBLISHER_ID_PARAM, SwarmPublisherSettings.ID,
      PARAM_URL, url
    ));
  }
}
