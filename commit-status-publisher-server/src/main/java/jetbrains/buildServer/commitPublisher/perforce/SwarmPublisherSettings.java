package jetbrains.buildServer.commitPublisher.perforce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
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
  public static final String PARAM_PASSWORD = "swarmPassword";

  public SwarmPublisherSettings(@NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems,
                                @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
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
    return myDescriptor.getPluginResourcesPath("perforce/swarmSettings.jsp");
  }

  @Nullable
  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType,
                                               @NotNull String buildFeatureId,
                                               @NotNull Map<String, String> params) {
    return new SwarmPublisher(this, buildType, buildFeatureId, params, myProblems);
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      @Override
      public Collection<InvalidProperty> process(Map<String, String> properties) {
        final List<InvalidProperty> result = new ArrayList<>();
        require(properties, PARAM_URL, result);
        require(properties, PARAM_USERNAME, result);
        require(properties, PARAM_PASSWORD, result);
        return result;
      }

      private void require(@NotNull Map<String, String> properties, @NotNull String parameterName, @NotNull List<InvalidProperty> result) {
        if (StringUtil.isEmptyOrSpaces(properties.get(parameterName))) {
          result.add(new InvalidProperty(parameterName, "is required"));
        }
      }
    };
  }
}
