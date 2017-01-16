package jetbrains.buildServer.commitPublisher.upsource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.upsource.data.UpsourceCurrentUser;
import jetbrains.buildServer.commitPublisher.upsource.data.UpsourceGetCurrentUserResult;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UpsourceSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  static final String ENDPOINT_BUILD_STATUS = "~buildStatus";
  static final String ENDPOINT_RPC = "~rpc";
  static final String QUERY_GET_CURRENT_USER = "getCurrentUser";
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

  public UpsourceSettings(@NotNull VcsModificationHistory vcsHistory,
                          @NotNull final ExecutorServices executorServices,
                          @NotNull PluginDescriptor descriptor,
                          @NotNull WebLinks links,
                          @NotNull CommitStatusPublisherProblems problems) {
    super(executorServices, descriptor, links, problems);
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
    return new UpsourcePublisher(buildType, buildFeatureId, myVcsHistory, myExecutorServices, myLinks, params, myProblems);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    String serverUrl = params.get(Constants.UPSOURCE_SERVER_URL);
    String projectId = params.get(Constants.UPSOURCE_PROJECT_ID);
    if (serverUrl == null || projectId == null)
      return getName();
    return "Upsource URL: " + WebUtil.escapeXml(serverUrl) + ", Upsource project ID: " + WebUtil.escapeXml(projectId);
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        checkContains(params, Constants.UPSOURCE_SERVER_URL, errors);
        checkContains(params, Constants.UPSOURCE_PROJECT_ID, errors);
        checkContains(params, Constants.UPSOURCE_USERNAME, errors);
        checkContains(params, Constants.UPSOURCE_PASSWORD, errors);
        return errors;
      }
    };
  }

  private void checkContains(@NotNull Map<String, String> params, @NotNull String key, @NotNull List<InvalidProperty> errors) {
    if (StringUtil.isEmpty(params.get(key)))
      errors.add(new InvalidProperty(key, "must be specified"));
  }

  public boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.commitStatusPublisher.upsourceEnabled");
  }

  @Override
  public boolean isTestConnectionSupported() {
    return false;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    String apiUrl = params.get(Constants.UPSOURCE_SERVER_URL);
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing Upsource Server URL parameter");
    String username = params.get(Constants.UPSOURCE_USERNAME);
    String password = params.get(Constants.UPSOURCE_PASSWORD);
    if (null == username || null == password)
      throw new PublisherException("Missing Upsource credentials");
    final String projectId = params.get(Constants.UPSOURCE_PROJECT_ID);
    String url = apiUrl + "/" + ENDPOINT_RPC + "/" + QUERY_GET_CURRENT_USER;
    try {
      HttpResponseProcessor processor = new DefaultHttpResponseProcessor() {
        @Override
        public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {
          super.processResponse(response);

          final HttpEntity entity = response.getEntity();
          if (null == entity) {
            throw new HttpPublisherException("Upsource publisher has received no response");
          }
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          entity.writeTo(bos);
          final String json = bos.toString("utf-8");
          UpsourceGetCurrentUserResult result = myGson.fromJson(json, UpsourceGetCurrentUserResult.class);
          if (null == result || null == result.result || null == result.result.userId) {
            throw new HttpPublisherException("Upsource publisher has received a malformed response");
          }
          UpsourceCurrentUser user = result.result;
          if (null != user.adminPermissionsInProjects) {
            for (String prjId : user.adminPermissionsInProjects) {
              if (prjId.equals(projectId)) {
                return;
              }
            }
          }
          throw new HttpPublisherException("Upsource does not grant enough permissions to publish a commit status");
        }
      };

      HttpHelper.get(url, username, password, null,
                      BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                      processor);

    } catch (Exception ex) {
      throw new PublisherException(String.format("Upsource publisher has failed to connect to project '%s'", projectId), ex);
    }
  }
}
