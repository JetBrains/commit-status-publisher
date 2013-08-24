package org.jetbrains.teamcity.publisher.gerrit;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.teamcity.publisher.CommitStatusPublisher;
import org.jetbrains.teamcity.publisher.CommitStatusPublisherSettings;

import java.util.*;

import static java.util.Arrays.asList;

public class GerritSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;

  public GerritSettings(@NotNull PluginDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  @NotNull
  public String getId() {
    return "gerritStatusPublisher";
  }

  @NotNull
  public String getName() {
    return "Gerrit";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("gerrit/gerritSettings.jsp");
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    Map<String, String> params = new HashMap<String, String>();
    params.put("successVote", "+1");
    params.put("failureVote", "-1");
    return params;
  }

  @Nullable
  public GerritPublisher createPublisher(@NotNull Map<String, String> params) {
    return new GerritPublisher(params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    GerritPublisher publisher = createPublisher(params);
    return "Gerrit " + publisher.getGerritServer() + "/" + publisher.getGerritProject();
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        for (String mandatoryParam : asList("gerritServer", "gerritProject", "gerritUsername", "successVote", "failureVote")) {
          if (params.get(mandatoryParam) == null)
            errors.add(new InvalidProperty(mandatoryParam, "must be specified"));
        }
        return errors;
      }
    };
  }
}
