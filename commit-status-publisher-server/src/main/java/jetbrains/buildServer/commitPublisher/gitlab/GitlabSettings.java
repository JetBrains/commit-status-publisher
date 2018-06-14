package jetbrains.buildServer.commitPublisher.gitlab;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabRepoInfo;
import jetbrains.buildServer.commitPublisher.gitlab.data.GitLabUserInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

import java.io.IOException;
import java.util.*;

public class GitlabSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private static final Pattern URL_WITH_API_SUFFIX = Pattern.compile("(.*)/api/v.");

  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.QUEUED);
    add(Event.REMOVED_FROM_QUEUE);
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

  public GitlabSettings(@NotNull ExecutorServices executorServices,
                        @NotNull PluginDescriptor descriptor,
                        @NotNull WebLinks links,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(executorServices, descriptor, links, problems, trustStoreProvider);
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
    String apiUrl = params.get(Constants.GITLAB_API_URL);
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing GitLab API URL parameter");
    String pathPrefix = getPathPrefix(apiUrl);
    Repository repository = GitlabPublisher.parseRepository(root, pathPrefix);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String token = params.get(Constants.GITLAB_TOKEN);
    if (null == token || token.length() == 0)
      throw new PublisherException("Missing GitLab API access token");
    try {
      ProjectInfoResponseProcessor processorPrj = new ProjectInfoResponseProcessor();
      HttpHelper.get(getProjectsUrl(apiUrl, repository.owner(), repository.repositoryName()),
                     null, null, Collections.singletonMap("PRIVATE-TOKEN", token),
                     BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processorPrj);
      if (processorPrj.getAccessLevel() < 30) {
        UserInfoResponseProcessor processorUser = new UserInfoResponseProcessor();
        HttpHelper.get(getUserUrl(apiUrl), null, null, Collections.singletonMap("PRIVATE-TOKEN", token),
                       BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processorUser);
        if (!processorUser.isAdmin()) {
          throw new HttpPublisherException("GitLab does not grant enough permissions to publish a commit status");
        }
      }
    } catch (Exception ex) {
      throw new PublisherException(String.format("GitLab publisher has failed to connect to %s/%s repository", repository.owner(), repository.repositoryName()), ex);
    }
  }

  @Nullable
  public static String getPathPrefix(final String apiUrl) {
    if (!URL_WITH_API_SUFFIX.matcher(apiUrl).matches()) return null;
    try {
      URI uri = new URI(apiUrl);
      String path = uri.getPath();
      return path.substring(0, path.length() - "/api/v4".length());
    } catch (URISyntaxException e) {
      return null;
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

  @NotNull
  public static String getProjectsUrl(@NotNull String apiUrl, @NotNull String owner, @NotNull String repo) {
    return apiUrl + "/projects/" + owner.replace(".", "%2E").replace("/", "%2F") + "%2F" + repo.replace(".", "%2E");
  }

  @NotNull
  public static String getUserUrl(@NotNull String apiUrl) {
    return apiUrl + "/user";
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents() {
    return mySupportedEvents;
  }

  private abstract class JsonResponseProcessor<T> extends DefaultHttpResponseProcessor {

    private final Class<T> myInfoClass;
    private T myInfo;

    JsonResponseProcessor(Class<T> infoClass) {
      myInfoClass = infoClass;
    }

    T getInfo() {
      return myInfo;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {

      super.processResponse(response);

      final String json = response.getContent();
      if (null == json) {
        throw new HttpPublisherException("GitLab publisher has received no response");
      }
      myInfo = myGson.fromJson(json, myInfoClass);
    }
  }

  private class ProjectInfoResponseProcessor extends JsonResponseProcessor<GitLabRepoInfo> {

    private int myAccessLevel;

    ProjectInfoResponseProcessor() {
      super(GitLabRepoInfo.class);
    }

    int getAccessLevel() {
      return myAccessLevel;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      myAccessLevel = 0;
      super.processResponse(response);
      GitLabRepoInfo repoInfo = getInfo();
      if (null == repoInfo || null == repoInfo.id || null == repoInfo.permissions) {
        throw new HttpPublisherException("GitLab publisher has received a malformed response");
      }
      if (null != repoInfo.permissions.project_access)
        myAccessLevel = repoInfo.permissions.project_access.access_level;
      if (null != repoInfo.permissions.group_access && myAccessLevel < repoInfo.permissions.group_access.access_level)
        myAccessLevel = repoInfo.permissions.group_access.access_level;
    }
  }

  private class UserInfoResponseProcessor extends JsonResponseProcessor<GitLabUserInfo> {

    private boolean myIsAdmin;

    UserInfoResponseProcessor() {
      super(GitLabUserInfo.class);
    }

    boolean isAdmin() {
      return myIsAdmin;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      myIsAdmin = false;
      super.processResponse(response);
      GitLabUserInfo userInfo = getInfo();
      if (null == userInfo || null == userInfo.id) {
        throw new HttpPublisherException("GitLab publisher has received a malformed response");
      }
      myIsAdmin = userInfo.is_admin;
    }
  }

}
