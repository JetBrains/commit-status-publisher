package jetbrains.buildServer.commitPublisher.upsource;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UpsourceSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final WebLinks myLinks;
  private final VcsModificationHistory myVcsHistory;

  public UpsourceSettings(@NotNull VcsModificationHistory vcsHistory,
                          @NotNull PluginDescriptor descriptor,
                          @NotNull WebLinks links) {
    myVcsHistory = vcsHistory;
    myDescriptor = descriptor;
    myLinks = links;
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
  public Map<String, String> getDefaultParameters() {
    return null;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    return null;
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    return new UpsourcePublisher(myVcsHistory, myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Upsource URL: " + params.get(Constants.UPSOURCE_SERVER_URL) + ", Upsource project ID: " + params.get(Constants.UPSOURCE_PROJECT_ID);
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (StringUtil.isEmpty(params.get(Constants.UPSOURCE_SERVER_URL)))
          errors.add(new InvalidProperty(Constants.UPSOURCE_SERVER_URL, "must be specified"));
        if (StringUtil.isEmpty(params.get(Constants.UPSOURCE_PROJECT_ID)))
          errors.add(new InvalidProperty(Constants.UPSOURCE_PROJECT_ID, "must be specified"));
        return errors;
      }
    };
  }

  public boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.commitStatusPublisher.upsourceEnabled");
  }
}
