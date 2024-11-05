

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

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import org.jetbrains.annotations.Nullable;

public class MissingVcsRootsReport extends HealthStatusReport {

  private static final String REPORT_TYPE = "CommitStatusPublisherMissesVcsRoot";
  private static final String DISPLAY_NAME
          = "Commit Status Publisher build feature refers to a VCS root that is not attached";
  private static final ItemCategory CATEGORY
          = new ItemCategory(REPORT_TYPE + "Category", DISPLAY_NAME, ItemSeverity.WARN);

  private final ProjectManager myProjectManager;

  public MissingVcsRootsReport(@NotNull ProjectManager projectManager) {
    myProjectManager = projectManager;
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
      List<VcsRootEntry> vcsRootEntries = bt.getVcsRootEntries();
      Set<String> vcsRootIds = new HashSet<String>();
      for (VcsRootEntry vcs: vcsRootEntries) {
        VcsRoot vcsRoot = vcs.getVcsRoot();
        if (vcsRoot instanceof SVcsRoot) {
          vcsRootIds.add(vcsRoot.getExternalId());
        }
      }
      Collection<SBuildFeatureDescriptor> features = bt.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE);
      for (SBuildFeatureDescriptor feature: features) {
        if (bt.isEnabled(feature.getId())) {
          Map<String, String> params = feature.getParameters();
          String vcsRootId = params.get(Constants.VCS_ROOT_ID_PARAM);
          if (StringUtil.isEmpty(vcsRootId)) {
            if (bt.getVcsRoots().isEmpty()) {
              reportIssue(consumer, bt, feature, vcsRootId);
            }
          } else {
            if (!vcsRootIds.contains(vcsRootId)) {
              reportIssue(consumer, bt, feature, vcsRootId);
            }
          }
        }
      }
    }
  }

  private void reportIssue(@NotNull HealthStatusItemConsumer consumer, @NotNull SBuildType bt, @NotNull SBuildFeatureDescriptor feature, @Nullable String vcsRootId) {
    String identity = REPORT_TYPE + "_BT_" + bt.getInternalId() + "_FEATURE_" + feature.getId();
    HashMap<String, Object> additionalData = new HashMap<String, Object>();
    additionalData.put("buildType", bt);
    additionalData.put("featureId", feature.getId());
    if (vcsRootId != null) {
      SVcsRoot missingVcsRoot = myProjectManager.findVcsRootByExternalId(vcsRootId);
      if (null != missingVcsRoot) // VCS root is there, but not attached
        additionalData.put("missingVcsRoot", missingVcsRoot);
    }
    consumer.consumeForBuildType(bt, new HealthStatusItem(identity, CATEGORY, additionalData));
  }
}