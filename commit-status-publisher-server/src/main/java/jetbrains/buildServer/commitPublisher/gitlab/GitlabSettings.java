package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.commitPublisher.BasePublisherSettings;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GitlabSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {


  public GitlabSettings(@NotNull ExecutorServices executorServices,
                        @NotNull PluginDescriptor descriptor,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems) {
    super(executorServices, descriptor, links, problems);
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GITLAB_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public String getName() {
    return "GitLab";
  }

  @Nullable
  @Override
  public String getEditSettingsUrl() {
    return "gitlab/gitlabSettings.jsp";
  }

  @NotNull
  @Override
  public GitlabPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new GitlabPublisher(buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems);
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String result = "Publish status to GitLab";
    String url = params.get(Constants.GITLAB_API_URL);
    if (url != null)
      result += " " + WebUtil.escapeXml(url);
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (params.get(Constants.GITLAB_API_URL) == null)
          errors.add(new InvalidProperty(Constants.GITLAB_API_URL, "must be specified"));
        if (params.get(Constants.GITLAB_TOKEN) == null)
          errors.add(new InvalidProperty(Constants.GITLAB_TOKEN, "must be specified"));
        return errors;
      }
    };
  }


  /**
   * GitLab REST API fails to interpret dots in user/group and project names
   * used within project ids in URLs for some calls.
   */
  public static String encodeDots(@NotNull String s) {
    if (!s.contains(".")
        || TeamCityProperties.getBoolean("teamcity.commitStatusPublisher.gitlab.disableUrlEncodingDots"))
      return s;
    return s.replace(".", "%2E");
  }
}
