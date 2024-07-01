

package jetbrains.buildServer.commitPublisher.gitea;

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaReceiveCommitStatus;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaRepoInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import jetbrains.buildServer.vcshostings.http.credentials.BearerTokenCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class GiteaSettings extends AuthTypeAwareSettings implements CommitStatusPublisherSettings {

  static final String DEFAULT_AUTH_TYPE = Constants.PASSWORD;
  static final String ACCESS_TOKEN_USERNAME = "x-token-auth";
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.COMMENTED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

  private static final Set<Event> mySupportedEventsWithQueued = new HashSet<Event>() {{
    add(Event.QUEUED);
    add(Event.REMOVED_FROM_QUEUE);
    addAll(mySupportedEvents);
  }};

  private final CommitStatusesCache<GiteaReceiveCommitStatus> myStatusesCache;

  public GiteaSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems,
                       @NotNull SSLTrustStoreProvider trustStoreProvider,
                       @NotNull UserModel userModel,
                       @NotNull SecurityContext securityContext) {
    super(descriptor, links, problems, trustStoreProvider, null, userModel, null, securityContext);
    myStatusesCache = new CommitStatusesCache<>();
  }

  @NotNull
  public String getId() {
    return Constants.GITEA_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "Gitea";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("gitea/giteaSettings.jsp");
  }


  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new GiteaPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, myStatusesCache);
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        final String authenticationType = params.getOrDefault(Constants.AUTH_TYPE, Constants.AUTH_TYPE_ACCESS_TOKEN);

        if (Constants.AUTH_TYPE_STORED_TOKEN.equalsIgnoreCase(authenticationType)) {
          if (StringUtil.isEmptyOrSpaces(params.get(Constants.TOKEN_ID))) {
            errors.add(new InvalidProperty(Constants.TOKEN_ID, "The refreshable token must be acquired"));
          }
        } else {
          params.remove(Constants.TOKEN_ID);
        }

        if (Constants.AUTH_TYPE_ACCESS_TOKEN.equalsIgnoreCase(authenticationType)) {
          if (StringUtil.isEmptyOrSpaces(params.get(Constants.GITEA_TOKEN)))
            errors.add(new InvalidProperty(Constants.GITEA_TOKEN, "Access token must be specified"));
        }
        else {
          params.remove(Constants.GITEA_TOKEN);
        }

        return errors;
      }
    };
  }


  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String apiUrl = params.getOrDefault(Constants.GITEA_API_URL, guessApiURL(root.getProperty("url")));
    if (StringUtil.isEmptyOrSpaces(apiUrl))
      throw new PublisherException("Missing Gitea API URL parameter");
    Repository repository = VCS_URL_PARSER.parseRepositoryUrl(root.getProperty("url"));
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String url = apiUrl + "/repos/" + repository.owner() + "/" + repository.repositoryName();
    HttpResponseProcessor<HttpPublisherException> processor = new DefaultHttpResponseProcessor() {

      @Override
      public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {

        super.processResponse(response);

        final String json = response.getContent();
        if (null == json) {
          throw new HttpPublisherException("Gitea publisher has received no response");
        }
        if (response.getStatusCode() == 401) {
          throw new HttpPublisherException(String.format(
            "Cannot access the \"%s\" repository. Ensure you are using a valid access token instead of a password (authentication via password is no longer available)",
            repository.repositoryName()));
        }
        GiteaRepoInfo repoInfo = myGson.fromJson(json, GiteaRepoInfo.class);
        if (repoInfo == null) {
          throw new HttpPublisherException("Could not parse Repository from returned Json");
        }
        if (repoInfo.permissions == null) {
          throw new HttpPublisherException("Could not parse Repository Permission from returned Json");
        }
        if (!repoInfo.permissions.push)
          throw new HttpPublisherException("No push permissions to repository. Ensure user and access token have proper rights. Access token needs write:repository rights.");
      }
    };
      final Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

      final HttpCredentials credentials = getCredentials(buildTypeOrTemplate.getProject(), root, params);

    try {
        IOGuard.allowNetworkCall(() -> {
          HttpHelper.get(url,
                         credentials,
                         headers,
                         BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                         trustStore(),
                         processor);
        });
      } catch (Exception ex) {
        throw new PublisherException(String.format("Gitea publisher has failed to connect to \"%s\" repository", repository.url()), ex);
      }
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  @Override
  @Nullable
  public String guessApiURL(@Nullable final String vcsRootUrl) {
    String hostUrl = super.guessApiURL(vcsRootUrl);
    if (hostUrl == null)
      return null;
    return hostUrl + "/api/v1";
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    final StringBuilder sb = new StringBuilder(super.describeParameters(params));

    sb.append(", authenticating via ");
    final String authType = getAuthType(params);
    switch (authType) {
      case Constants.PASSWORD:
        sb.append("username / password credentials");
        break;

      case Constants.AUTH_TYPE_STORED_TOKEN:
        sb.append("access token");
        break;

      case Constants.AUTH_TYPE_VCS:
        sb.append("VCS Root credentials");
        break;

      default:
        sb.append("unknown authentication type");
    }

    return sb.toString();
  }

  @NotNull
  @Override
  protected String getDefaultAuthType() {
    return DEFAULT_AUTH_TYPE;
  }

  @Nullable
  @Override
  protected String getUsername(@NotNull Map<String, String> params) {
    return params.get(Constants.GITEA_USERNAME);
  }

  @Nullable
  @Override
  protected String getPassword(@NotNull Map<String, String> params) {
    return params.get(Constants.GITEA_PASSWORD);
  }


  @Override
  public HttpCredentials getVcsRootPasswordCredentials(@NotNull VcsRoot root, @Nullable Map<String, String> vcsProperties) throws PublisherException {
    if (vcsProperties == null) vcsProperties = root.getProperties();

    final String username = vcsProperties.get("username");
    final String password = vcsProperties.get("secure:password");

    if (ACCESS_TOKEN_USERNAME.equals(username)) {
      if (StringUtil.isEmpty(password))
        throw new PublisherException("Unable to get Access Token credentials from VCS Root \" + root.getVcsName()");
      return new BearerTokenCredentials(password);
    }
    return super.getVcsRootPasswordCredentials(root, vcsProperties);
  }

  @Override
  public boolean isEnabled() {
    return TeamCityProperties.getBoolean("teamcity.commitStatusPublisher.gitea.enabled");
  }
}
