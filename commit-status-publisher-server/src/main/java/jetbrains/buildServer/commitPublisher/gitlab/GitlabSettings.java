package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabAccessLevel;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabRepoInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class GitlabSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

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
    return new GitlabPublisher(this, buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems);
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    Repository repository = GitlabPublisher.parseRepository(root);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String apiUrl = params.get(Constants.GITLAB_API_URL);
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing GitLab API URL parameter");
    String token = params.get(Constants.GITLAB_TOKEN);
    if (null == token || token.length() == 0)
      throw new PublisherException("Missing GitLab API access token");
    String url = apiUrl + "/projects/" + encodeDots(repository.owner())
                 + "%2F" + encodeDots(repository.repositoryName());
    try {
      HttpResponseProcessor processor = new DefaultHttpResponseProcessor() {
        @Override
        public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {

          super.processResponse(response);

          final HttpEntity entity = response.getEntity();
          if (null == entity) {
            throw new HttpPublisherException("GitLab publisher has received no response");
          }
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          entity.writeTo(bos);
          final String json = bos.toString("utf-8");
          GitLabRepoInfo repoInfo = myGson.fromJson(json, GitLabRepoInfo.class);
          int accessLevel = 0;
          if (null == repoInfo || null == repoInfo.id || null == repoInfo.permissions) {
            throw new HttpPublisherException("GitLab publisher has received a malformed response");
          }
          if (null != repoInfo.permissions.project_access)
            accessLevel = repoInfo.permissions.project_access.access_level;
          if (null != repoInfo.permissions.group_access && accessLevel < repoInfo.permissions.group_access.access_level)
            accessLevel = repoInfo.permissions.group_access.access_level;
          if (accessLevel < 30) {
            throw new HttpPublisherException("GitLab does not grant enough permissions to publish a commit status");
          }
        }
      };

      HttpHelper.get(url, null, null, Collections.singletonMap("PRIVATE-TOKEN", token), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, processor);
    } catch (Exception ex) {
      throw new PublisherException(String.format("GitLab publisher has failed to connect to %s/%s repository", repository.owner(), repository.repositoryName()), ex);
    }
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String result = super.describeParameters(params);
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
          errors.add(new InvalidProperty(Constants.GITLAB_API_URL, "GitLab API URL must be specified"));
        if (params.get(Constants.GITLAB_TOKEN) == null)
          errors.add(new InvalidProperty(Constants.GITLAB_TOKEN, "Access token must be specified"));
        return errors;
      }
    };
  }

  /**
   * GitLab REST API fails to interpret dots in (user/group or user/subgroup/) and project names
   * used within project ids in URLs for some calls.
   */
  public static String encodeDots(@NotNull String s) {
    if (!s.contains(".")
        || TeamCityProperties.getBoolean("teamcity.commitStatusPublisher.gitlab.disableUrlEncodingDots"))
      return s;
    return s.replace(".", "%2E").replace("/","%2F");
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents() {
    return mySupportedEvents;
  }
}
