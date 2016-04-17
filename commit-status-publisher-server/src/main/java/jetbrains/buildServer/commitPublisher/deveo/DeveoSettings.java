package jetbrains.buildServer.commitPublisher.deveo;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DeveoSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final WebLinks myLinks;

  public DeveoSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links) {
    myDescriptor = descriptor;
    myLinks= links;
  }

  @NotNull
  public String getId() {
    return Constants.DEVEO_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "Deveo";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("deveo/deveoSettings.jsp");
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    return null;
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    return new DeveoPublisher(myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Deveo";
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.DEVEO_PLUGIN_KEY)))
          errors.add(new InvalidProperty(Constants.DEVEO_PLUGIN_KEY, "must be specified"));

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.DEVEO_COMPANY_KEY)))
          errors.add(new InvalidProperty(Constants.DEVEO_COMPANY_KEY, "must be specified"));

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.DEVEO_API_HOSTNAME)))
          errors.add(new InvalidProperty(Constants.DEVEO_API_HOSTNAME, "must be specified"));

        return errors;
      }
    };
  }

  public boolean isEnabled() {
    return true;
  }
}
