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

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CommitStatusPublisherFeature extends BuildFeature {

  public static final String TYPE = "commit-status-publisher";
  private final CommitStatusPublisherFeatureController myController;
  private final PublisherManager myPublisherManager;

  public CommitStatusPublisherFeature(@NotNull CommitStatusPublisherFeatureController controller,
                                      @NotNull PublisherManager publisherManager) {
    myController = controller;
    myPublisherManager = publisherManager;
  }

  @NotNull
  @Override
  public String getType() {
    return TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Commit status publisher";
  }

  @Nullable
  @Override
  public String getEditParametersUrl() {
    return myController.getUrl();
  }

  @Override
  public boolean isMultipleFeaturesPerBuildTypeAllowed() {
    return true;
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String publisherId = params.get(Constants.PUBLISHER_ID_PARAM);
    if (publisherId == null)
      return "";
    CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
    if (settings == null)
      return "";
    return settings.describeParameters(params);
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        String publisherId = params.get(Constants.PUBLISHER_ID_PARAM);
        if (StringUtil.isEmptyOrSpaces(publisherId) || DummyPublisherSettings.ID.equals(publisherId)) {
          errors.add(new InvalidProperty(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));
          return errors;
        }

        CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
        if (settings == null)
          return errors;
        PropertiesProcessor proc = settings.getParametersProcessor();
        if (proc != null)
          errors.addAll(proc.process(params));
        return errors;
      }
    };
  }

  @Override
  public boolean isRequiresAgent() {
    return false;
  }
}
