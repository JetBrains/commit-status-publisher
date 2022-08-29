package jetbrains.buildServer.swarm.commitPublisher;

import java.util.*;
import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kir
 */
public class SwarmPublisherSettings extends BasePublisherSettings {

  static final String ID = "perforceSwarmPublisher";

  public static final String PARAM_URL = "swarmUrl";
  public static final String PARAM_USERNAME = "swarmUser";
  public static final String PARAM_PASSWORD = "secure:swarmPassword";
  public static final String PARAM_CREATE_SWARM_TEST = "createSwarmTest";

  private static final Set<CommitStatusPublisher.Event> ourSupportedEvents = new HashSet<CommitStatusPublisher.Event>() {{
    add(CommitStatusPublisher.Event.QUEUED);
    add(CommitStatusPublisher.Event.REMOVED_FROM_QUEUE);
    add(CommitStatusPublisher.Event.STARTED);
    add(CommitStatusPublisher.Event.FAILURE_DETECTED);
    add(CommitStatusPublisher.Event.INTERRUPTED);
    add(CommitStatusPublisher.Event.FINISHED);
    add(CommitStatusPublisher.Event.MARKED_AS_SUCCESSFUL);
  }};

  private final SwarmClientManager myClientManager;

  public SwarmPublisherSettings(@NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems,
                                @NotNull SSLTrustStoreProvider trustStoreProvider,
                                @NotNull SwarmClientManager clientManager) {
    super(descriptor, links, problems, trustStoreProvider);
    myClientManager = clientManager;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getName() {
    return "Perforce Helix Swarm";
  }

  @NotNull
  public String describeParameters(@NotNull final Map<String, String> params) {
    return super.describeParameters(params) + "; URL: " + params.get(PARAM_URL);
  }

  @Nullable
  @Override
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("swarm/swarmSettings.jsp");
  }

  @Nullable
  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType,
                                               @NotNull String buildFeatureId,
                                               @NotNull Map<String, String> params) {
    return new SwarmPublisher(this, buildType, buildFeatureId, params, myProblems, myLinks, myClientManager.getSwarmClient(params));
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      @Override
      public Collection<InvalidProperty> process(Map<String, String> properties) {
        final List<InvalidProperty> result = new ArrayList<>();
        require(properties, PARAM_URL, result, "Server URL");
        require(properties, PARAM_USERNAME, result, "Username");
        require(properties, PARAM_PASSWORD, result, "Ticket or password");

        final String url = properties.get(PARAM_URL);
        if (StringUtil.isNotEmpty(url) && !url.startsWith("http")) {
          properties.put(PARAM_URL, "http://" + url);
        }
        return result;
      }

      private void require(@NotNull Map<String, String> properties, @NotNull String parameterName, @NotNull List<InvalidProperty> result, @NotNull String what) {
        if (StringUtil.isEmptyOrSpaces(properties.get(parameterName))) {
          result.add(new InvalidProperty(parameterName, what + " is required"));
        }
      }
    };
  }

  @Override
  protected Set<CommitStatusPublisher.Event> getSupportedEvents(SBuildType buildType, Map<String, String> params) {
    return ourSupportedEvents;
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate,
                             @NotNull VcsRoot root,
                             @NotNull Map<String, String> params) throws PublisherException {

    IOGuard.allowNetworkCall(() -> {
      myClientManager.getSwarmClient(params).testConnection();
    });
  }
}
