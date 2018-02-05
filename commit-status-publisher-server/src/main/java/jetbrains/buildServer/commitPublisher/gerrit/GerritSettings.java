package jetbrains.buildServer.commitPublisher.gerrit;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

import java.util.*;

import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private final ExtensionHolder myExtensionHolder;
  private final Map<String, String> myMandatoryProperties = new HashMap<String, String>() {{
          put(Constants.GERRIT_SERVER, "Server URL");
          put(Constants.GERRIT_PROJECT, "Gerrit project");
          put(Constants.GERRIT_USERNAME, "Username");
          put(Constants.GERRIT_SUCCESS_VOTE, "Success vote");
          put(Constants.GERRIT_FAILURE_VOTE, "Failure vote");
          put(TEAMCITY_SSH_KEY_PROP, "SSH key");
  }};
  private GerritClient myGerritClient;
  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.FINISHED);
  }};


  public GerritSettings(@NotNull ExecutorServices executorServices,
                        @NotNull PluginDescriptor descriptor,
                        @NotNull ExtensionHolder extensionHolder,
                        @NotNull GerritClient gerritClient,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(executorServices, descriptor, links, problems, trustStoreProvider);
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
    return new GerritPublisher(this, buildType, buildFeatureId, myGerritClient, myLinks, params, myProblems);
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
        for (Map.Entry<String, String> mandatoryParam : myMandatoryProperties.entrySet()) {
          if (params.get(mandatoryParam.getKey()) == null)
            errors.add(new InvalidProperty(mandatoryParam.getKey(), String.format("%s must be specified", mandatoryParam.getValue())));
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

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName());
  }

  public boolean isEnabled() {
    return true;
  }

  @Override
  protected Set<Event> getSupportedEvents() {
    return mySupportedEvents;
  }
}
