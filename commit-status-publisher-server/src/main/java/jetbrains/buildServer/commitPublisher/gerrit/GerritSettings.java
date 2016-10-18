package jetbrains.buildServer.commitPublisher.gerrit;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private final WebLinks myLinks;
  private final String[] myMandatoryProperties = new String[] {
          Constants.GERRIT_SERVER, Constants.GERRIT_PROJECT, Constants.GERRIT_USERNAME,
          Constants.GERRIT_SUCCESS_VOTE, Constants.GERRIT_FAILURE_VOTE, TEAMCITY_SSH_KEY_PROP};
  private CommitStatusPublisherProblems myProblems;


  public GerritSettings(@NotNull PluginDescriptor descriptor,
                        @NotNull ExtensionHolder extensionHolder,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems) {
    myDescriptor = descriptor;
    myExtensionHolder = extensionHolder;
    myLinks = links;
    myProblems = problems;
  }

  @NotNull
  public String getId() {
    return Constants.GERRIT_PUBLISHER_ID;
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
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    return null;
  }

  @Nullable
  public GerritPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    Collection<ServerSshKeyManager> extensions = myExtensionHolder.getExtensions(ServerSshKeyManager.class);
    if (extensions.isEmpty()) {
      return new GerritPublisher(buildType, buildFeatureId, null, myLinks, params, myProblems);
    } else {
      return new GerritPublisher(buildType, buildFeatureId, extensions.iterator().next(), myLinks, params, myProblems);
    }
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Gerrit " + WebUtil.escapeXml(params.get(Constants.GERRIT_SERVER)) + "/" + WebUtil.escapeXml(params.get(Constants.GERRIT_PROJECT));
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
