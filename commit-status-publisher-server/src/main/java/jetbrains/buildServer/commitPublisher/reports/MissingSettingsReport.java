

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

package jetbrains.buildServer.commitPublisher.reports;

import com.google.common.collect.ImmutableSet;
import java.util.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.PublisherManager;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.healthStatus.*;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.commitPublisher.Constants.*;
import static jetbrains.buildServer.commitPublisher.space.Constants.SPACE_PUBLISHER_ID;

public class MissingSettingsReport extends HealthStatusReport {

  private static final String REPORT_TYPE = "CommitStatusPublisherMissesSettings";
  private static final String DISPLAY_NAME
          = "Commit Status Publisher build feature refers to a missing settings";
  private static final ItemCategory CATEGORY
          = new ItemCategory(REPORT_TYPE + "Category", DISPLAY_NAME, ItemSeverity.WARN);
  private static final Set<String> SUPPORTED_PUBLISHER_IDS = ImmutableSet.of(SPACE_PUBLISHER_ID);

  private final PublisherManager myPublisherManager;

  public MissingSettingsReport(@NotNull PublisherManager publisherManager) {
    myPublisherManager = publisherManager;
  }

  @NotNull
  @Override
  public String getType() {
    return REPORT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope healthStatusScope) {
    return healthStatusScope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    for (SBuildType bt : scope.getBuildTypes()) {
      Collection<SBuildFeatureDescriptor> features = bt.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE);
      for (SBuildFeatureDescriptor feature: features) {
        if (bt.isEnabled(feature.getId())) {
          Map<String, String> params = feature.getParameters();
          String publisherId = params.get(Constants.PUBLISHER_ID_PARAM);
          if (publisherId == null || !SUPPORTED_PUBLISHER_IDS.contains(publisherId)) {
            continue;
          }

          CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
          if (settings != null) {
            Map<String, Object> healthItemData = settings.checkHealth(bt, params);
            if (healthItemData != null) {
              String identity = REPORT_TYPE + "_BT_" + bt.getInternalId() + "_FEATURE_" + feature.getId();
              healthItemData.put("buildType", bt);
              healthItemData.put("featureId", feature.getId());
              consumer.consumeForBuildType(bt, new HealthStatusItem(identity, CATEGORY, healthItemData));
            }
          }
        }
      }
    }
  }
}