/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudCommitBuildStatus;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudRepoInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class BitbucketCloudSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  static final String DEFAULT_API_URL = "https://api.bitbucket.org/";
  static final String DEFAULT_AUTH_TYPE = Constants.AUTH_TYPE_PASSWORD;
  static final BitbucketCloudRepositoryParser VCS_PROPERTIES_PARSER = new BitbucketCloudRepositoryParser();

  private String myDefaultApiUrl = DEFAULT_API_URL;
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
  private final OAuthConnectionsManager myOAuthConnectionsManager;
  private final OAuthTokensStorage myOAuthTokensStorage;

  public BitbucketCloudSettings(@NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems,
                                @NotNull SSLTrustStoreProvider trustStoreProvider,
                                @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                                @NotNull OAuthTokensStorage oAuthTokensStorage) {
    super(descriptor, links, problems, trustStoreProvider);
    myOAuthConnectionsManager = oAuthConnectionsManager;
    myOAuthTokensStorage = oAuthTokensStorage;
    myStatusesCache = new CommitStatusesCache<>();
  }

  void setDefaultApiUrl(@NotNull String url) {
    myDefaultApiUrl = url;
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
    return new BitbucketCloudPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, myStatusesCache, myOAuthTokensStorage);
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();

        final String authType = getAuthType(params);
        switch (authType) {
          case Constants.AUTH_TYPE_PASSWORD:
            params.remove(Constants.TOKEN_ID);

            if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_USERNAME))) {
              errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_USERNAME, "Username must be specified"));
            }

            if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_PASSWORD))) {
              errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_PASSWORD, "Password must be specified"));
            }
            break;

          case Constants.AUTH_TYPE_ACCESS_TOKEN:
            params.remove(Constants.BITBUCKET_CLOUD_USERNAME);
            params.remove(Constants.BITBUCKET_CLOUD_PASSWORD);

            if (StringUtil.isEmpty(params.get(Constants.TOKEN_ID))) {
              errors.add(new InvalidProperty(Constants.TOKEN_ID, "No token configured"));
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
    String url = myDefaultApiUrl + "/2.0/repositories/" + repository.owner() + "/" + repoName;
    HttpResponseProcessor processor = new DefaultHttpResponseProcessor() {
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

    final String authType = getAuthType(params);
    final String username;
    final String password;
    switch (authType) {

      case Constants.AUTH_TYPE_PASSWORD:
        username = params.get(Constants.BITBUCKET_CLOUD_USERNAME);
        password = params.get(Constants.BITBUCKET_CLOUD_PASSWORD);
        break;

      case Constants.AUTH_TYPE_ACCESS_TOKEN:
        username = null;
        password = null;
        final String tokenId = params.get(Constants.TOKEN_ID);
        if (StringUtil.isEmptyOrSpaces(tokenId)) {
          throw new PublisherException("authentication type is set to access token, but no token id is configured");
        }
        final OAuthToken token = myOAuthTokensStorage.getRefreshableToken(root.getExternalId(), tokenId);
        if (token == null) {
          throw new PublisherException("configured token was not found in storage ID: " + tokenId);
        }
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccessToken());
        break;

      default:
        throw new PublisherException("unsupported authentication type " + authType);
    }

    try {
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.get(url,
                       username,
                       password,
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
    return isBuildQueuedSupported(buildType, params) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(@NotNull SProject project, @NotNull SUser user) {
    return myOAuthConnectionsManager.getAvailableConnectionsOfType(project, BitBucketOAuthProvider.TYPE)
                                    .stream()
                                    .collect(Collectors.toMap(Function.identity(),
                                                              c -> !myOAuthTokensStorage.getUserTokens(c.getId(), user, project, false).isEmpty()));
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    final StringBuilder sb = new StringBuilder(super.describeParameters(params));

    sb.append(", authenticating via ");
    final String authType = getAuthType(params);
    switch (authType) {
      case Constants.AUTH_TYPE_PASSWORD:
        sb.append("username / password credentials");
        break;

      case Constants.AUTH_TYPE_ACCESS_TOKEN:
        sb.append("access token");
        break;

      default:
        sb.append("unknown authentication type");
    }

    return sb.toString();
  }

  @NotNull
  static String getAuthType(@NotNull Map<String, String> params) {
    return params.getOrDefault(Constants.AUTH_TYPE, DEFAULT_AUTH_TYPE);
  }

}
