package jetbrains.buildServer.commitPublisher.upsource;

import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UpsourceSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private final VcsModificationHistory myVcsHistory;

  public UpsourceSettings(@NotNull VcsModificationHistory vcsHistory,
                          @NotNull final ExecutorServices executorServices,
                          @NotNull PluginDescriptor descriptor,
                          @NotNull WebLinks links) {
    super(executorServices, descriptor, links);
    myVcsHistory = vcsHistory;
  }

  @NotNull
  public String getId() {
    return Constants.UPSOURCE_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "JetBrains Upsource";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("upsource/upsourceSettings.jsp");
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    return new UpsourcePublisher(myVcsHistory, myExecutorServices, myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    String serverUrl = params.get(Constants.UPSOURCE_SERVER_URL);
    String projectId = params.get(Constants.UPSOURCE_PROJECT_ID);
    if (serverUrl == null || projectId == null)
      return getName();
    return "Upsource URL: " + WebUtil.escapeXml(serverUrl) + ", Upsource project ID: " + WebUtil.escapeXml(projectId);
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        checkContains(params, Constants.UPSOURCE_SERVER_URL, errors);
        checkContains(params, Constants.UPSOURCE_PROJECT_ID, errors);
        checkContains(params, Constants.UPSOURCE_USERNAME, errors);
        checkContains(params, Constants.UPSOURCE_PASSWORD, errors);
        return errors;
      }
    };
  }

  private void checkContains(@NotNull Map<String, String> params, @NotNull String key, @NotNull List<InvalidProperty> errors) {
    if (StringUtil.isEmpty(params.get(key)))
      errors.add(new InvalidProperty(key, "must be specified"));
  }

  public boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.commitStatusPublisher.upsourceEnabled");
  }
}
