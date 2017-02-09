package jetbrains.buildServer.commitPublisher.gerrit;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private final ExtensionHolder myExtensionHolder;
  private final String[] myMandatoryProperties = new String[] {
          Constants.GERRIT_SERVER, Constants.GERRIT_PROJECT, Constants.GERRIT_USERNAME,
          Constants.GERRIT_SUCCESS_VOTE, Constants.GERRIT_FAILURE_VOTE, TEAMCITY_SSH_KEY_PROP};
  private GerritClient myGerritClient;


  public GerritSettings(@NotNull ExecutorServices executorServices,
                        @NotNull PluginDescriptor descriptor,
                        @NotNull ExtensionHolder extensionHolder,
                        @NotNull GerritClient gerritClient,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems) {
    super(executorServices, descriptor, links, problems);
    myExtensionHolder = extensionHolder;
    myGerritClient = gerritClient;
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
    return new GerritPublisher(buildType, buildFeatureId, myGerritClient, myLinks, params, myProblems);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return super.describeParameters(params) + ": " + WebUtil.escapeXml(params.get(Constants.GERRIT_SERVER)) + "/" + WebUtil.escapeXml(params.get(Constants.GERRIT_PROJECT));
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

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    try {
      myGerritClient.testConnection(
        new GerritConnectionDetails(buildTypeOrTemplate.getProject(), params.get(Constants.GERRIT_PROJECT),
                                    params.get(Constants.GERRIT_SERVER), params.get(Constants.GERRIT_USERNAME),
                                    params.get(ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP))
      );
    } catch (Exception e) {
      throw new PublisherException("Gerrit publisher connection test has failed", e);
    }
  }

  public boolean isEnabled() {
    return true;
  }

}
