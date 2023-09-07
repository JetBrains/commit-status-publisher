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

package jetbrains.buildServer.commitPublisher.stash;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.stash.data.JsonStashBuildStatus;
import jetbrains.buildServer.commitPublisher.stash.data.StashError;
import jetbrains.buildServer.commitPublisher.stash.data.StashRepoInfo;
import jetbrains.buildServer.commitPublisher.stash.data.StashServerInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.vcshostings.url.InvalidUriException;
import jetbrains.buildServer.vcshostings.url.ServerURI;
import jetbrains.buildServer.vcshostings.url.ServerURIParser;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT;
import static jetbrains.buildServer.commitPublisher.PropertyChecks.mandatoryString;
import static jetbrains.buildServer.commitPublisher.stash.StashPublisher.PROP_PUBLISH_QUEUED_BUILD_STATUS;

public class StashSettings extends AuthTypeAwareSettings implements CommitStatusPublisherSettings {

  static final String DEFAULT_AUTH_TYPE = Constants.PASSWORD;
  static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

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

  @NotNull
  private final CommitStatusesCache<JsonStashBuildStatus> myStatusesCache;

  public StashSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems,
                       @NotNull SSLTrustStoreProvider trustStoreProvider,
                       @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                       @NotNull OAuthTokensStorage oAuthTokensStorage,
                       @NotNull UserModel userModel,
                       @NotNull SecurityContext securityContext) {
    super(descriptor, links, problems, trustStoreProvider, oAuthTokensStorage, userModel, oAuthConnectionsManager, securityContext);
    myStatusesCache = new CommitStatusesCache<>();
  }

  @NotNull
  public String getId() {
    return Constants.STASH_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "Bitbucket Server / Data Center";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("stash/stashSettings.jsp");
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new StashPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems, myStatusesCache);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    final StringBuilder sb = new StringBuilder(super.describeParameters(params));

    final String url = params.get(Constants.STASH_BASE_URL);
    if (url != null) {
      sb.append(": ").append(WebUtil.escapeXml(url));
    }

    sb.append(", authenticating via ");
    final String authType = getAuthType(params);
    switch (authType) {
      case Constants.PASSWORD:
        sb.append("username / password credentials");
        break;
      case Constants.AUTH_TYPE_STORED_TOKEN:
        sb.append("access token");
        break;
      default:
        sb.append("unknown authentication type");
    }

    return sb.toString();
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<>();

        final String authType = getAuthType(params);
        switch(authType) {
          case Constants.PASSWORD:
            params.remove(Constants.TOKEN_ID);
            mandatoryString(Constants.STASH_USERNAME, "Username must be specified", params, errors);
            mandatoryString(Constants.STASH_PASSWORD, "Password must be specified", params, errors);
            break;

          case Constants.AUTH_TYPE_STORED_TOKEN:
            params.remove(Constants.STASH_USERNAME);
            params.remove(Constants.STASH_PASSWORD);
            mandatoryString(Constants.TOKEN_ID, "No token configured", params, errors);
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

  // /rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String vcsRootUrl = root.getProperty("url");
    if (null == vcsRootUrl) {
      throw new PublisherException("Missing VCS root URL");
    }
    final Repository repository = VCS_URL_PARSER.parseRepositoryUrl(vcsRootUrl);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String apiUrl = params.getOrDefault(Constants.STASH_BASE_URL, guessApiURL(root.getProperty("url")));
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Bitbucket Server API URL parameter");
    String url = apiUrl + "/rest/api/1.0/projects/" + repository.owner() + "/repos/" + repository.repositoryName();

    final Map<String, String> headers = ImmutableMap.of(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

    final HttpCredentials credentials = getCredentials(root, params);

    HttpResponseProcessor<HttpPublisherException> processor = new DefaultHttpResponseProcessor() {
      @Override
      public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {

        super.processResponse(response);

        final String json = response.getContent();
        if (null == json) {
          throw new HttpPublisherException("Stash publisher has received no response");
        }
        StashRepoInfo repoInfo = myGson.fromJson(json, StashRepoInfo.class);
        if (null == repoInfo)
          throw new HttpPublisherException("Bitbucket Server publisher has received a malformed response");
        if (null != repoInfo.errors && !repoInfo.errors.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          for (StashError err: repoInfo.errors) {
            sb.append("\n");
            sb.append(err.message);
          }
          String pluralS = "";
          if (repoInfo.errors.size() > 1)
            pluralS = "s";
          throw new HttpPublisherException(String.format("Bitbucket Server publisher error%s:%s", pluralS, sb.toString()));
        }
      }
    };
    try {
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.get(url, credentials, headers,
                       BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processor);
      });
    } catch (Exception ex) {
      throw new PublisherException(String.format("Bitbucket Server publisher has failed to connect to \"%s\" repository", repository.url()), ex);
    }
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return "jetbrains.git".equals(vcsRoot.getVcsName());
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  protected boolean isBuildQueuedSupported(final SBuildType buildType) {
    return TeamCityProperties.getBoolean(PROP_PUBLISH_QUEUED_BUILD_STATUS) || super.isBuildQueuedSupported(buildType);
  }

  @Nullable
  @Override
  protected String retrieveServerVersion(@NotNull String url) throws PublisherException {
    try {
      ServerVersionResponseProcessor processor = new ServerVersionResponseProcessor();
      IOGuard.allowNetworkCall(() -> HttpHelper.get(url + "/rest/api/1.0/application-properties", null, null, DEFAULT_CONNECTION_TIMEOUT, null, processor));
      return processor.getVersion();
    } catch (Exception e) {
      throw new PublisherException("Failed to obtain Bitbucket Server version", e);
    }
  }

  @NotNull
  @Override
  public List<OAuthConnectionDescriptor> getOAuthConnections(@NotNull SProject project, @NotNull SUser user) {
    return myOAuthConnectionsManager.getAvailableConnectionsOfType(project, Constants.STASH_OAUTH_PROVIDER_TYPE).stream()
      .sorted(CONNECTION_DESCRIPTOR_NAME_COMPARATOR)
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  protected String getDefaultAuthType() {
    return DEFAULT_AUTH_TYPE;
  }

  @Nullable
  @Override
  protected String getUsername(@NotNull Map<String, String> params) {
    return params.get(Constants.STASH_USERNAME);
  }

  @Nullable
  @Override
  protected String getPassword(@NotNull Map<String, String> params) {
    return params.get(Constants.STASH_PASSWORD);
  }

  private class ServerVersionResponseProcessor extends DefaultHttpResponseProcessor {

    private String myVersion;

    @Nullable
    String getVersion() {
      return myVersion;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      super.processResponse(response);
      final String json = response.getContent();
      if (null == json) {
        throw new HttpPublisherException("Bitbucket Server publisher has received no response");
      }
      StashServerInfo serverInfo = myGson.fromJson(json, StashServerInfo.class);
      if (null == serverInfo)
        throw new HttpPublisherException("Bitbucket Server publisher has received a malformed response");
      myVersion = serverInfo.version;
    }
  }

}


