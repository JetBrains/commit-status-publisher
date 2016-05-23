package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class StashSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final WebLinks myLinks;

  public StashSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links) {
    myDescriptor = descriptor;
    myLinks= links;
  }

  @NotNull
  public String getId() {
    return Constants.STASH_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "Bitbucket Server (Atlassian Stash)";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("stash/stashSettings.jsp");
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
    return new StashPublisher(myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    StashPublisher voter = (StashPublisher) createPublisher(params);
    return getName() + " " + voter.getBaseUrl();
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (params.get(Constants.STASH_BASE_URL) == null)
          errors.add(new InvalidProperty(Constants.STASH_BASE_URL, "must be specified"));
        return errors;
      }
    };
  }

  public boolean isEnabled() {
    return true;
  }
}
