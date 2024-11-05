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

package jetbrains.buildServer.commitPublisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.SVcsRootEx;
import org.assertj.core.api.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;


public class PublisherManagerTest extends CommitStatusPublisherTestBase {

  private static final String SETTINGS_SUPPORTING_FEATURELESS_ID = "settings-supporting-featureless";

  private PublisherManager myPublisherManager;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myServer.registerExtension(CommitStatusPublisherSettings.class, SETTINGS_SUPPORTING_FEATURELESS_ID, new SettingsSupportingFeatureless());

    myPublisherManager = new PublisherManager(myServer);
  }

  @Test
  public void createSupplementaryPublishers_noVcsRoots() {
    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, Collections.emptyMap());

    then(supplementaryPublishers).isEmpty();
  }

  @Test
  public void createSupplementaryPublishers_oneRootWithBuildFeature() {
    final SVcsRoot vcsRoot = vcsRoot();
    final CommitStatusPublisher publisher = publisherUsingVcsRoot("build-feature-1", vcsRoot);

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, publisherMap(publisher));

    then(supplementaryPublishers).isEmpty();
  }

  @Test
  public void createSupplementaryPublishers_oneRootWithBuildFeatureByExternalId() {
    final SVcsRoot vcsRoot = vcsRoot();
    final CommitStatusPublisher publisher = publisherUsingVcsRootExternalId("build-feature-1", vcsRoot);

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, publisherMap(publisher));

    then(supplementaryPublishers).isEmpty();
  }

  @Test
  public void createSupplementaryPublishers_oneRootWithBuildFeatureByAlias() {
    final SVcsRoot vcsRoot = vcsRoot();
    final String alias = vcsRoot.getExternalId();
    vcsRoot.setExternalId("new_name");
    final CommitStatusPublisher publisher = publisherUsingVcsRootAlias("build-feature-1", alias);

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, publisherMap(publisher));

    then(supplementaryPublishers).isEmpty();
  }

  @Test
  public void createSupplementaryPublishers_multipleRootsBuildFeatureCoversAll() {
    vcsRoot();
    vcsRoot();
    vcsRoot();
    final CommitStatusPublisher publisher = publisherForAll("build-feature-all");

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, publisherMap(publisher));

    then(supplementaryPublishers).isEmpty();
  }

  @Test
  public void createSupplementaryPublishers_multipleRootsPartiallyCoveredByFeature() {
    final SVcsRoot firstRoot = vcsRoot();
    final SVcsRoot secondRoot = vcsRoot();
    final SVcsRoot uncoveredRoot = vcsRoot();
    final CommitStatusPublisher firstPublisher = publisherUsingVcsRoot("build-feature-1", firstRoot);
    final CommitStatusPublisher secondPublisher = publisherUsingVcsRoot("build-feature-2", secondRoot);

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, publisherMap(firstPublisher, secondPublisher));

    then(supplementaryPublishers).hasSize(1);
    then(supplementaryPublishers.entrySet()).have(publisherEntry(notEmpty(), forVcsRootId(uncoveredRoot.getId())));
  }

  @Test
  public void createSupplementaryPublishers_oneRootNoFeature() {
    final SVcsRoot vcsRoot = vcsRoot();

    final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(myBuildType, Collections.emptyMap());

    then(supplementaryPublishers).hasSize(1);
    then(supplementaryPublishers.entrySet()).have(publisherEntry(notEmpty(), forVcsRootId(vcsRoot.getId())));
  }

  @NotNull
  private static CommitStatusPublisher publisherUsingVcsRoot(@NotNull String buildFeatureId, @NotNull SVcsRoot vcsRoot) {
    final CommitStatusPublisher mockPublisher = Mockito.mock(CommitStatusPublisher.class);
    Mockito.when(mockPublisher.getBuildFeatureId()).thenReturn(buildFeatureId);
    Mockito.when(mockPublisher.getVcsRootId()).thenReturn(String.valueOf(vcsRoot.getId()));
    return mockPublisher;
  }

  @NotNull
  private static CommitStatusPublisher publisherUsingVcsRootExternalId(@NotNull String buildFeatureId, @NotNull SVcsRoot vcsRoot) {
    final CommitStatusPublisher mockPublisher = Mockito.mock(CommitStatusPublisher.class);
    Mockito.when(mockPublisher.getBuildFeatureId()).thenReturn(buildFeatureId);
    Mockito.when(mockPublisher.getVcsRootId()).thenReturn(vcsRoot.getExternalId());
    return mockPublisher;
  }

  @NotNull
  private static CommitStatusPublisher publisherUsingVcsRootAlias(@NotNull String buildFeatureId, @NotNull String alias) {
    final CommitStatusPublisher mockPublisher = Mockito.mock(CommitStatusPublisher.class);
    Mockito.when(mockPublisher.getBuildFeatureId()).thenReturn(buildFeatureId);
    Mockito.when(mockPublisher.getVcsRootId()).thenReturn(alias);
    return mockPublisher;
  }

  @NotNull
  private static CommitStatusPublisher publisherForAll(@NotNull String buildFeatureId) {
    final CommitStatusPublisher mockPublisher = Mockito.mock(CommitStatusPublisher.class);
    Mockito.when(mockPublisher.getBuildFeatureId()).thenReturn(buildFeatureId);
    return mockPublisher;
  }

  @NotNull
  private static Map<String, CommitStatusPublisher> publisherMap(CommitStatusPublisher... publishers) {
    return Arrays.stream(publishers).collect(Collectors.toMap(CommitStatusPublisher::getBuildFeatureId, Function.identity()));
  }

  private SVcsRoot vcsRoot() {
    final SVcsRootEx root = createVcsRoot("jetbrains.git", myProject);
    myBuildType.addVcsRoot(root);
    return root;
  }

  @NotNull
  private static Condition<Map.Entry<String, CommitStatusPublisher>> publisherEntry(@NotNull Condition<String> keyCondition,
                                                                                    @NotNull Condition<CommitStatusPublisher> publisherCondition) {
    return new Condition<Map.Entry<String, CommitStatusPublisher>>() {
      @Override
      public boolean matches(Map.Entry<String, CommitStatusPublisher> value) {
        then(value.getKey()).has(keyCondition);
        then(value.getValue()).has(publisherCondition);
        return true;
      }
    };
  }

  private static Condition<String> notEmpty() {
    return new Condition<String>() {
      @Override
      public boolean matches(String value) {
        return StringUtil.isNotEmpty(value);
      }
    };
  }

  private static Condition<CommitStatusPublisher> forVcsRootId(long vcsRootId) {
    return new Condition<CommitStatusPublisher>() {
      @Override
      public boolean matches(CommitStatusPublisher value) {
        return String.valueOf(vcsRootId).equals(value.getVcsRootId());
      }
    };
  }

  static class SettingsSupportingFeatureless extends DummyPublisherSettings {

    @NotNull
    @Override
    public String getId() {
      return SETTINGS_SUPPORTING_FEATURELESS_ID;
    }

    @NotNull
    @Override
    public String getName() {
      return "settings supporting featureless publishing";
    }

    @Override
    public boolean isFeatureLessPublishingSupported(@NotNull SBuildType buildType) {
      return true;
    }

    @Nullable
    @Override
    public CommitStatusPublisher createFeaturelessPublisher(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot) {
      return publisherUsingVcsRoot("featureless_for_bt_" + buildType.getBuildTypeId() + "_" + vcsRoot.getId(), vcsRoot);
    }
  }
}