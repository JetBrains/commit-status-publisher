package jetbrains.buildServer.commitPublisher.gerrit;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;

import java.util.*;

import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private final WebLinks myLinks;
  private final String[] myMandatoryProperties = new String[] {
          Constants.GERRIT_SERVER, Constants.GERRIT_PROJECT, Constants.GERRIT_USERNAME,
          Constants.GERRIT_SUCCESS_VOTE, Constants.GERRIT_FAILURE_VOTE, TEAMCITY_SSH_KEY_PROP};


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
  public Map<String, String> getDefaultParameters() {
    Map<String, String> params = new HashMap<String, String>();
    params.put(Constants.GERRIT_SUCCESS_VOTE, "+1");
    params.put(Constants.GERRIT_FAILURE_VOTE, "-1");
    return params;
  }

  @Nullable
  public GerritPublisher createPublisher(@NotNull Map<String, String> params) {
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
        for (String mandatoryParam : myMandatoryProperties) {
          if (params.get(mandatoryParam) == null)
            errors.add(new InvalidProperty(mandatoryParam, "must be specified"));
        }
        return errors;
      }
    };
  }

  public boolean isEnabled() {
    return true;
  }
}
