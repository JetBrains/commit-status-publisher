package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.stash.data.StashError;
import jetbrains.buildServer.commitPublisher.stash.data.StashRepoInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class StashSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  public StashSettings(@NotNull final ExecutorServices executorServices,
                       @NotNull PluginDescriptor descriptor,
                       @NotNull WebLinks links,
                       @NotNull CommitStatusPublisherProblems problems) {
    super(executorServices, descriptor, links, problems);
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
    return new StashPublisher(buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    String url = params.get(Constants.STASH_BASE_URL);
    return getName() + (url != null ? " " + WebUtil.escapeXml(url) : "");
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        if (params.get(Constants.STASH_BASE_URL) == null)
          errors.add(new InvalidProperty(Constants.STASH_BASE_URL, "must be specified"));
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
    Repository repository = GitRepositoryParser.parseRepository(vcsRootUrl);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    String apiUrl = params.get(Constants.STASH_BASE_URL);
    if (null == apiUrl || apiUrl.length() == 0)
      throw new PublisherException("Missing BitBucket Server API URL parameter");
    String url = apiUrl + "/rest/api/1.0/projects/" + repository.owner() + "/repos/" + repository.repositoryName();
    try {
      HttpResponseProcessor processor = new DefaultHttpResponseProcessor() {
        @Override
        public void processResponse(HttpResponse response) throws HttpPublisherException, IOException {

          super.processResponse(response);

          final HttpEntity entity = response.getEntity();
          if (null == entity) {
            throw new HttpPublisherException("Stash publisher has received no response");
          }
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          entity.writeTo(bos);
          final String json = bos.toString("utf-8");
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

      HttpHelper.get(url, params.get(Constants.STASH_USERNAME), params.get(Constants.STASH_PASSWORD),
        Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, processor);
    } catch (Exception ex) {
      throw new PublisherException(String.format("Bitbucket Server publisher has failed to connect to %s/%s repository", repository.owner(), repository.repositoryName()), ex);
    }
  }


}
