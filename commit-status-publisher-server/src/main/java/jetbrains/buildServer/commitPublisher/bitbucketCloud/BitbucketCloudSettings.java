

/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudCommitBuildStatus;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudRepoInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import jetbrains.buildServer.vcshostings.http.credentials.BearerTokenCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class BitbucketCloudSettings extends AuthTypeAwareSettings implements CommitStatusPublisherSettings {

  static final String DEFAULT_API_URL = "https://api.bitbucket.org/";
  static final String DEFAULT_AUTH_TYPE = Constants.PASSWORD;
  static final String ACCESS_TOKEN_USERNAME = "x-token-auth";
  static final BitbucketCloudRepositoryParser VCS_PROPERTIES_PARSER = new BitbucketCloudRepositoryParser();

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

  private final CommitStatusesCache<BitbucketCloudCommitBuildStatus> myStatusesCache;

  private final ProjectManager myProjectManager;

  public BitbucketCloudSettings(@NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems,
                                @NotNull SSLTrustStoreProvider trustStoreProvider,
                                @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                                @NotNull OAuthTokensStorage oAuthTokensStorage,
                                @NotNull UserModel userModel,
                                @NotNull SecurityContext securityContext,
                                @NotNull ProjectManager projectManager) {
    super(descriptor, links, problems, trustStoreProvider, oAuthTokensStorage, userModel, oAuthConnectionsManager, securityContext);
    myStatusesCache = new CommitStatusesCache<>();
    myProjectManager = projectManager;
  }

  protected String getDefaultApiUrl() {
    return DEFAULT_API_URL;
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
    return new BitbucketCloudPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, myStatusesCache);
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();

        final String authType = getAuthType(params);
        switch (authType) {
          case Constants.PASSWORD:
            params.remove(Constants.TOKEN_ID);

            if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_USERNAME))) {
              errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_USERNAME, "Username must be specified"));
            }

            if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_PASSWORD))) {
              errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_PASSWORD, "Password must be specified"));
            }
            break;

          case Constants.AUTH_TYPE_STORED_TOKEN:
            params.remove(Constants.BITBUCKET_CLOUD_USERNAME);
            params.remove(Constants.BITBUCKET_CLOUD_PASSWORD);

            if (StringUtil.isEmpty(params.get(Constants.TOKEN_ID))) {
              errors.add(new InvalidProperty(Constants.TOKEN_ID, "No token configured"));
            }
            break;

          case Constants.AUTH_TYPE_VCS:
            params.remove(Constants.BITBUCKET_CLOUD_USERNAME);
            params.remove(Constants.BITBUCKET_CLOUD_PASSWORD);
            params.remove(Constants.TOKEN_ID);

            String vcsRootId = params.get(Constants.VCS_ROOT_ID_PARAM);
            SVcsRoot vcsRoot = null == vcsRootId ? null : myProjectManager.findVcsRootByExternalId(vcsRootId);
            if (null != vcsRoot) {
              String vcsUrl = vcsRoot.getProperty("url");
              if (!StringUtil.isEmpty(vcsUrl) && !vcsUrl.trim().toLowerCase().startsWith("http"))
                errors.add(new InvalidProperty(Constants.AUTH_TYPE, "Credentials can not be extracted as the selected VCS root uses non-HTTP(S) fetch URL"));
            }

            break;

          default:
            errors.add(new InvalidProperty(Constants.AUTH_TYPE, "Unsupported authentication type"));
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
  public boolean isFQDNTeamCityUrlRequired() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    Repository repository = VCS_PROPERTIES_PARSER.parseRepository(root);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    final String repoName = repository.repositoryName();
    String url = getDefaultApiUrl() + "/2.0/repositories/" + repository.owner() + "/" + repoName;
    HttpResponseProcessor<HttpPublisherException> processor = new DefaultHttpResponseProcessor() {
      @Override
      public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {

        super.processResponse(response);

        final String json = response.getContent();
        if (null == json) {
          throw new HttpPublisherException("Stash publisher has received no response");
        }
        BitbucketCloudRepoInfo repoInfo = myGson.fromJson(json, BitbucketCloudRepoInfo.class);
        if (null == repoInfo)
          throw new HttpPublisherException("Bitbucket Cloud publisher has received a malformed response");
        if (null == repoInfo.slug || !repoInfo.slug.equals(repoName)) {
          throw new HttpPublisherException("No repository found");
        }
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
      throw new PublisherException(String.format("Bitbucket Cloud publisher has failed to connect to \"%s\" repository", repository.url()), ex);
    }
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName()) || "mercurial".equals(vcsRoot.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  @NotNull
  @Override
  public List<OAuthConnectionDescriptor> getOAuthConnections(@NotNull SProject project, @NotNull SUser user) {
    return myOAuthConnectionsManager.getAvailableConnectionsOfType(project, BitBucketOAuthProvider.TYPE)
                                    .stream()
                                    .sorted(CONNECTION_DESCRIPTOR_NAME_COMPARATOR)
                                    .collect(Collectors.toList());
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
    return params.get(Constants.BITBUCKET_CLOUD_USERNAME);
  }

  @Nullable
  @Override
  protected String getPassword(@NotNull Map<String, String> params) {
    return params.get(Constants.BITBUCKET_CLOUD_PASSWORD);
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

    // TW-88869: try extract username from url if not provided explicitly
    if (StringUtil.isEmpty(username) && !StringUtil.isEmpty(password)) {
      try {
        URI uri = new URI(vcsProperties.get(Constants.GIT_URL_PARAMETER));
        String usernameFromUri = uri.getUserInfo();
        if (!StringUtil.isEmpty(usernameFromUri)) {
          return new UsernamePasswordCredentials(usernameFromUri, password);
        }
      } catch (URISyntaxException ignored) {}
    }

    return super.getVcsRootPasswordCredentials(root, vcsProperties);
  }
}