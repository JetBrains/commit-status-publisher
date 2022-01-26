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

package jetbrains.buildServer.commitPublisher.upsource;

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.upsource.data.UpsourceCurrentUser;
import jetbrains.buildServer.commitPublisher.upsource.data.UpsourceGetCurrentUserResult;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

public class UpsourceSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  static final String ENDPOINT_BUILD_STATUS = "~buildStatus";
  static final String ENDPOINT_RPC = "~rpc";
  static final String QUERY_GET_CURRENT_USER = "getCurrentUser";
  static final String ENDPOINT_TEST_CONNECTION = "~buildStatusTestConnection";
  static final String PROJECT_FIELD = "project";
  static final String KEY_FIELD = "key";
  static final String STATE_FIELD = "state";
  static final String BUILD_URL_FIELD = "url";
  static final String BUILD_NAME_FIELD = "name";
  static final String DESCRIPTION_FIELD = "description";
  static final String REVISION_FIELD = "revision";
  static final String REVISION_MESSAGE_FIELD = "revisionMessage";
  static final String REVISION_DATE_FIELD = "revisionDate";


  private final VcsModificationHistory myVcsHistory;
  private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
    add(Event.STARTED);
    add(Event.FINISHED);
    add(Event.MARKED_AS_SUCCESSFUL);
    add(Event.INTERRUPTED);
    add(Event.FAILURE_DETECTED);
  }};

  public UpsourceSettings(@NotNull VcsModificationHistory vcsHistory,
                          @NotNull PluginDescriptor descriptor,
                          @NotNull WebLinks links,
                          @NotNull CommitStatusPublisherProblems problems,
                          @NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(descriptor, links, problems, trustStoreProvider);
    myVcsHistory = vcsHistory;
  }

  @NotNull
  public String getId() {
    return Constants.UPSOURCE_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "JetBrains Upsource";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("upsource/upsourceSettings.jsp");
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return new UpsourcePublisher(this, buildType, buildFeatureId, myVcsHistory, myLinks, params, myProblems);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    String serverUrl = params.get(Constants.UPSOURCE_SERVER_URL);
    String projectId = params.get(Constants.UPSOURCE_PROJECT_ID);
    String result = super.describeParameters(params);
    if (serverUrl != null && projectId != null)
      result += ": " + WebUtil.escapeXml(serverUrl) + ", Project ID: " + WebUtil.escapeXml(projectId);
    return result;
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        checkContains(params, Constants.UPSOURCE_SERVER_URL, "Server URL", errors);
        checkContains(params, Constants.UPSOURCE_PROJECT_ID, "Project id", errors);
        checkContains(params, Constants.UPSOURCE_USERNAME, "Username", errors);
        checkContains(params, Constants.UPSOURCE_PASSWORD, "Password", errors);
        return errors;
      }
    };
  }

  private void checkContains(@NotNull Map<String, String> params, @NotNull String key, @NotNull String fieldName, @NotNull List<InvalidProperty> errors) {
    if (StringUtil.isEmpty(params.get(key)))
      errors.add(new InvalidProperty(key, String.format("%s must be specified", fieldName)));
  }

  public boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.commitStatusPublisher.upsourceEnabled");
  }

  @Override
  public boolean isTestConnectionSupported() {
    return true;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String apiUrl = HttpHelper.stripTrailingSlash(params.get(Constants.UPSOURCE_SERVER_URL));
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Upsource Server URL parameter");
    String username = params.get(Constants.UPSOURCE_USERNAME);
    String password = params.get(Constants.UPSOURCE_PASSWORD);
    if (null == username || null == password)
      throw new PublisherException("Missing Upsource credentials");
    final String projectId = params.get(Constants.UPSOURCE_PROJECT_ID);
    String urlPost = apiUrl + "/" + ENDPOINT_TEST_CONNECTION;
    String urlGet = apiUrl + "/" + ENDPOINT_RPC + "/" + QUERY_GET_CURRENT_USER;
    try {
      Map<String, String> data = new HashMap<String, String>();
      data.put(UpsourceSettings.PROJECT_FIELD, projectId);
      // Newer versions of Upsource support special test connection call, that works correctly for their CI-specific authentication
      IOGuard.allowNetworkCall(() -> {
        HttpHelper.post(urlPost, username, password, myGson.toJson(data), ContentType.APPLICATION_JSON, null,
                        BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(),
                        new DefaultHttpResponseProcessor());
      });
    } catch (Exception ex) {
      try {
        // If the newer method fails, we assume it may be an older version of Upsource, and test connection in a regular way
        IOGuard.allowNetworkCall(() -> {
          HttpHelper.get(urlGet, username, password, null,
                         BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, trustStore(),
                         new TestConnectionResponseProcessor(projectId));
        });
      } catch (Exception ex2) {
        throw new PublisherException(String.format("Upsource publisher has failed to connect to project '%s'", projectId), ex2);
      }
    }
  }

  @Override
  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return mySupportedEvents;
  }

  private class TestConnectionResponseProcessor  extends DefaultHttpResponseProcessor {
    private final String myProjectId;

    public TestConnectionResponseProcessor(String projectId) {
      super();
      myProjectId = projectId;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      super.processResponse(response);

      final String json = response.getContent();
      if (null == json) {
        throw new HttpPublisherException("Upsource publisher has received no response");
      }
      UpsourceGetCurrentUserResult result = myGson.fromJson(json, UpsourceGetCurrentUserResult.class);
      if (null == result || null == result.result || null == result.result.userId) {
        throw new HttpPublisherException("Upsource publisher has received a malformed response");
      }
      UpsourceCurrentUser user = result.result;
      if (null != user.adminPermissionsInProjects) {
        for (String prjId : user.adminPermissionsInProjects) {
          if (prjId.equals(myProjectId)) {
            return;
          }
        }
      }
      throw new HttpPublisherException("Upsource does not grant enough permissions to publish a commit status");
    }
  }
}
