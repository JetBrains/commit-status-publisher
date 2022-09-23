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

package jetbrains.buildServer.commitPublisher.gitea;

import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaCommitStatus;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaRepoInfo;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaUserInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

public class GiteaSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  private static final Pattern URL_WITH_API_SUFFIX = Pattern.compile("(.*)/api/v.");

  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

  private static final Set<Event> mySupportedEventsWithQueued = new HashSet<Event>() {{
    add(Event.QUEUED);
    add(Event.REMOVED_FROM_QUEUE);
    addAll(mySupportedEvents);
  }};

  private final CommitStatusesCache<GiteaCommitStatus> myStatusesCache;

  public GiteaSettings(@NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems,
                       @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
    myStatusesCache = new CommitStatusesCache<>();
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GITEA_PUBLISHER_ID;
  }

  @NotNull
  @Override
  public String getName() {
    return "Gitea";
  }

  @Nullable
  @Override
  public String getEditSettingsUrl() {
    return "gitea/giteaSettings.jsp";
  }

  @NotNull
  @Override
  public GiteaPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new GiteaPublisher(this, buildType, buildFeatureId, params, myProblems, myLinks, myStatusesCache);
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String apiUrl = params.get(Constants.GITEA_API_URL);
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Gitea API URL parameter");
    String pathPrefix = getPathPrefix(apiUrl);
    Repository repository = GiteaPublisher.parseRepository(root, pathPrefix);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String token = params.get(Constants.GITEA_TOKEN);
    if (null == token || token.length() == 0)
      throw new PublisherException("Missing Gitea API access token");
      try {
        IOGuard.allowNetworkCall(() -> {
          ProjectInfoResponseProcessor processorPrj = new ProjectInfoResponseProcessor();
          String url = getProjectsUrl(apiUrl, repository.owner(), repository.repositoryName());
          url += "?access_token=" + token;
          HttpHelper.get(url, null, null, null,
                         BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processorPrj);
          if (!processorPrj.hasPushAccess()) {
            UserInfoResponseProcessor processorUser = new UserInfoResponseProcessor();
            url = getUserUrl(apiUrl);
            url += "?access_token=" + token;
            HttpHelper.get(url, null, null, null,
                           BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(), processorUser);
            if (!processorUser.isAdmin()) {
              throw new HttpPublisherException("Gitea does not grant enough permissions to publish a commit status");
            }
          }
        });
      } catch (Exception ex) {
        throw new PublisherException(String.format("Gitea publisher has failed to connect to \"%s\" repository", repository.url()), ex);
      }
  }

  @Nullable
  public static String getPathPrefix(final String apiUrl) {
    if (!URL_WITH_API_SUFFIX.matcher(apiUrl).matches()) return null;
    try {
      URI uri = new URI(apiUrl);
      String path = uri.getPath();
      return path.substring(0, path.length() - "/api/v1".length());
    } catch (URISyntaxException e) {
      return null;
    }
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String result = super.describeParameters(params);
    String url = params.get(Constants.GITEA_API_URL);
    if (url != null)
      result += " " + WebUtil.escapeXml(url);
    return result;
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (params.get(Constants.GITEA_API_URL) == null)
          errors.add(new InvalidProperty(Constants.GITEA_API_URL, "Gitea API URL must be specified"));
        if (params.get(Constants.GITEA_TOKEN) == null)
          errors.add(new InvalidProperty(Constants.GITEA_TOKEN, "Access token must be specified"));
        return errors;
      }
    };
  }

  @NotNull
  public static String getProjectsUrl(@NotNull String apiUrl, @NotNull String owner, @NotNull String repo) {
    return apiUrl + "/repos/" + owner.replace(".", "%2E").replace("/", "%2F") + "/" + repo.replace(".", "%2E");
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
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return isBuildQueuedSupported(buildType, params) ? mySupportedEventsWithQueued : mySupportedEvents;
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
        throw new HttpPublisherException("Gitea publisher has received no response");
      }
      myInfo = myGson.fromJson(json, myInfoClass);
    }
  }

  private class ProjectInfoResponseProcessor extends JsonResponseProcessor<GiteaRepoInfo> {

    private boolean myHasPushAccess;

    ProjectInfoResponseProcessor() {
      super(GiteaRepoInfo.class);
    }

    boolean hasPushAccess() {
      return myHasPushAccess;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      myHasPushAccess = false;
      super.processResponse(response);
      GiteaRepoInfo repoInfo = getInfo();
      if (null == repoInfo || null == repoInfo.id || null == repoInfo.permissions) {
        throw new HttpPublisherException("Gitea publisher has received a malformed response");
      }
      myHasPushAccess = repoInfo.permissions.push;
    }
  }

  private class UserInfoResponseProcessor extends JsonResponseProcessor<GiteaUserInfo> {

    private boolean myIsAdmin;

    UserInfoResponseProcessor() {
      super(GiteaUserInfo.class);
    }

    boolean isAdmin() {
      return myIsAdmin;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      myIsAdmin = false;
      super.processResponse(response);
      GiteaUserInfo userInfo = getInfo();
      if (null == userInfo || null == userInfo.id) {
        throw new HttpPublisherException("Gitea publisher has received a malformed response");
      }
      myIsAdmin = userInfo.is_admin;
    }
  }

}
