package org.jetbrains.teamcity.publisher.gerrit;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.teamcity.publisher.BaseCommitStatusSettings;

import java.util.*;

import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings extends BaseCommitStatusSettings {

  private final PluginDescriptor myDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private final WebLinks myLinks;

  public GerritSettings(@NotNull PluginDescriptor descriptor,
                        @NotNull ExtensionHolder extensionHolder,
                        @NotNull WebLinks links) {
    myDescriptor = descriptor;
    myExtensionHolder = extensionHolder;
    myLinks = links;
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
  public Collection<String> getMandatoryParameters() {
    final List<String> params = new ArrayList<String>();
    params.add("gerritServer");
    params.add("gerritProject");
    params.add("gerritUsername");
    params.add("gerritLabel");
    params.add("successVote");
    params.add("failureVote");
    params.add(TEAMCITY_SSH_KEY_PROP);

    return Collections.unmodifiableList(params);
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    Map<String, String> params = new HashMap<String, String>();
    params.put("gerritLabel", "Verified");
    params.put("successVote", "+1");
    params.put("failureVote", "-1");
    return params;
  }

  @Nullable
  public GerritPublisher createPublisher(@NotNull Map<String, String> params) {
    params = getUpdatedParametersForPublisher(params);
    Collection<ServerSshKeyManager> extensions = myExtensionHolder.getExtensions(ServerSshKeyManager.class);
    if (extensions.isEmpty()) {
      return new GerritPublisher(null, myLinks, params);
    } else {
      return new GerritPublisher(extensions.iterator().next(), myLinks, params);
    }
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
        for (String mandatoryParam : getMandatoryParameters()) {
          if (params.get(mandatoryParam) == null)
            errors.add(new InvalidProperty(mandatoryParam, "must be specified"));
        }
        return errors;
      }
    };
  }
}
