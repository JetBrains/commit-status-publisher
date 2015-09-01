package org.jetbrains.teamcity.publisher.stash;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.teamcity.publisher.BaseCommitStatusSettings;
import org.jetbrains.teamcity.publisher.CommitStatusPublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StashSettings extends BaseCommitStatusSettings {

  private final PluginDescriptor myDescriptor;
  private final WebLinks myLinks;

  public StashSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links) {
    myDescriptor = descriptor;
    myLinks= links;
  }

  @NotNull
  public String getId() {
    return "atlassianStashPublisher";
  }

  @NotNull
  public String getName() {
    return "Atlassian Stash";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("stash/stashSettings.jsp");
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    params = getUpdatedParametersForPublisher(params);
    return new StashPublisher(myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    StashPublisher voter = (StashPublisher) createPublisher(params);
    return "Atlassian Stash " + voter.getBaseUrl();
  }

  @Nullable
  public Collection<String> getMandatoryParameters() {
    final List<String> params = new ArrayList<String>();
    params.add("stashBaseUrl");

    return Collections.unmodifiableList(params);
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (params.get("stashBaseUrl") == null)
          errors.add(new InvalidProperty("stashBaseUrl", "must be specified"));
        return errors;
      }
    };
  }
}
