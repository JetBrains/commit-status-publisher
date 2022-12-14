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

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.stash.data.JsonStashBuildStatus;
import jetbrains.buildServer.commitPublisher.stash.data.StashError;
import jetbrains.buildServer.commitPublisher.stash.data.StashRepoInfo;
import jetbrains.buildServer.commitPublisher.stash.data.StashServerInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT;
import static jetbrains.buildServer.commitPublisher.stash.StashPublisher.PROP_PUBLISH_QUEUED_BUILD_STATUS;

public class StashSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

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

  private final CommitStatusesCache<JsonStashBuildStatus> myStatusesCache;

  public StashSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems,
                       @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
    myStatusesCache = new CommitStatusesCache<>();
  }

  @NotNull
  public String getId() {
    return Constants.STASH_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "Bitbucket Server (Atlassian Stash)";
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
    String url = params.get(Constants.STASH_BASE_URL);
    return super.describeParameters(params) + (url != null ? ": " + WebUtil.escapeXml(url) : "");
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (params.get(Constants.STASH_BASE_URL) == null)
          errors.add(new InvalidProperty(Constants.STASH_BASE_URL, "Server URL must be specified"));
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
    String apiUrl = params.get(Constants.STASH_BASE_URL);
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Bitbucket Server API URL parameter");
    String url = apiUrl + "/rest/api/1.0/projects/" + repository.owner() + "/repos/" + repository.repositoryName();
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
        HttpHelper.get(url, getCredentials(params),
                       Collections.singletonMap("Accept", "application/json"),
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
    return isBuildQueuedSupported(buildType, params) ? mySupportedEventsWithQueued : mySupportedEvents;
  }

  protected boolean isBuildQueuedSupported(final SBuildType buildType, final Map<String, String> params) {
    return TeamCityProperties.getBoolean(PROP_PUBLISH_QUEUED_BUILD_STATUS) || super.isBuildQueuedSupported(buildType, params);
  }

  @Nullable
  private static HttpCredentials getCredentials(Map<String, String> params) {
    final String username = params.get(Constants.STASH_USERNAME);
    final String password = params.get(Constants.STASH_PASSWORD);
    return (username != null && password != null) ? new UsernamePasswordCredentials(username, password) : null;
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


