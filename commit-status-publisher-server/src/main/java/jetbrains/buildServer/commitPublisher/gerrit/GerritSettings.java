

package jetbrains.buildServer.commitPublisher.gerrit;

import java.util.*;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private final ExtensionHolder myExtensionHolder;
  private final Map<String, String> myMandatoryProperties = new HashMap<String, String>() {{
          put(GerritConstants.SERVER, "Server URL");
          put(GerritConstants.PROJECT, "Gerrit project");
          put(GerritConstants.USERNAME, "Username");
          put(GerritConstants.LABEL, "Gerrit Label");
          put(GerritConstants.SUCCESS_VOTE, "Success vote");
          put(GerritConstants.FAILURE_VOTE, "Failure vote");
          put(TEAMCITY_SSH_KEY_PROP, "SSH key");
  }};
  private GerritClient myGerritClient;
  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.FINISHED);
  }};


  public GerritSettings(@NotNull PluginDescriptor descriptor,
                        @NotNull ExtensionHolder extensionHolder,
                        @NotNull GerritClient gerritClient,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
    myExtensionHolder = extensionHolder;
    myGerritClient = gerritClient;
  }

  @NotNull
  public String getId() {
    return GerritConstants.PUBLISHER_ID;
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
    params.put(GerritConstants.LABEL, "Verified");
    params.put(GerritConstants.SUCCESS_VOTE, "+1");
    params.put(GerritConstants.FAILURE_VOTE, "-1");
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
    return super.describeParameters(params) + ": " + WebUtil.escapeXml(params.get(GerritConstants.SERVER)) + "/" + WebUtil.escapeXml(params.get(GerritConstants.PROJECT));
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
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
        new GerritConnectionDetails(buildTypeOrTemplate.getProject(), params.get(GerritConstants.PROJECT),
                                    params.get(GerritConstants.SERVER), params.get(GerritConstants.USERNAME),
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
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return mySupportedEvents;
  }
}