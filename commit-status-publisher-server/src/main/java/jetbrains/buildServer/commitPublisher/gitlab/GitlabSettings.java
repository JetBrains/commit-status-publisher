package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GitlabSettings implements CommitStatusPublisherSettings {

  private final WebLinks myLinks;
  private final ExecutorServices myExecutorServices;

  public GitlabSettings(@NotNull ExecutorServices executorServices, @NotNull WebLinks links) {
    myExecutorServices = executorServices;
    myLinks = links;
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

  @Nullable
  @Override
  public Map<String, String> getDefaultParameters() {
    return null;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    return null;
  }

  @NotNull
  @Override
  public GitlabPublisher createPublisher(@NotNull Map<String, String> params) {
    return new GitlabPublisher(myExecutorServices, myLinks, params);
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String result = "Publish status to GitLab";
    GitlabPublisher publisher = createPublisher(params);
    String url = publisher.getApiUrl();
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

  @Override
  public boolean isEnabled() {
    return true;
  }
}
