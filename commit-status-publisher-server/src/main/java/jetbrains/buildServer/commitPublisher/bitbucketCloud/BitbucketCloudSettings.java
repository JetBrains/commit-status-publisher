package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
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

public class BitbucketCloudSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {
  private static final Pattern URL_WITH_FQDN_PATTERN = Pattern.compile("[a-z]+://[^\\.:/]+\\.(.+)");

  public BitbucketCloudSettings(@NotNull final ExecutorServices executorServices,
                                @NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems) {
    super(executorServices, descriptor, links, problems);
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
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new BitbucketCloudPublisher(buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems);
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

        if (!URL_WITH_FQDN_PATTERN.matcher(myLinks.getRootUrl()).matches())
          errors.add(new InvalidProperty(Constants.PUBLISHER_ID_PARAM, "requires the TeamCity Server URL to contain fully qualified domain name"));

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_USERNAME)))
          errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_USERNAME, "must be specified"));

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_PASSWORD)))
          errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_PASSWORD, "must be specified"));

        return errors;
      }
    };
  }
}
