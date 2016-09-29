package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class StashSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  public StashSettings(@NotNull final ExecutorServices executorServices,
                       @NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links) {
    super(executorServices, descriptor, links);
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
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    return new StashPublisher(myExecutorServices, myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    StashPublisher voter = (StashPublisher) createPublisher(params);
    String url = voter.getBaseUrl();
    return getName() + (url != null ? " " + WebUtil.escapeXml(url) : "");
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
}
