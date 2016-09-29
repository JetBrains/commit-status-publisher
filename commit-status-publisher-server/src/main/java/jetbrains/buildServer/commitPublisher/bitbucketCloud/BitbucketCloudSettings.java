package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BitbucketCloudSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final WebLinks myLinks;
  private final ExecutorServices myExecutorServices;

  public BitbucketCloudSettings(@NotNull final ExecutorServices executorServices,
                                @NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links) {
    myDescriptor = descriptor;
    myLinks= links;
    myExecutorServices = executorServices;
  }

  @NotNull
  public String getId() {
    return Constants.BITBUCKET_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "Bitbucket Cloud";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("bitbucketCloud/bitbucketCloudSettings.jsp");
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
    return new BitbucketCloudPublisher(myExecutorServices, myLinks, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Bitbucket Cloud";
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_USERNAME)))
          errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_USERNAME, "must be specified"));

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_PASSWORD)))
          errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_PASSWORD, "must be specified"));

        return errors;
      }
    };
  }

  public boolean isEnabled() {
    return true;
  }
}
