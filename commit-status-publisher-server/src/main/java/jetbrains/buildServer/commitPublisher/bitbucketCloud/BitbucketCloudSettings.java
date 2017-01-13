package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudRepoInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BitbucketCloudSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

  final static String DEFAULT_API_URL = "https://api.bitbucket.org/";

  private String myDefaultApiUrl = DEFAULT_API_URL;

  public BitbucketCloudSettings(@NotNull final ExecutorServices executorServices,
                                @NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems) {
    super(executorServices, descriptor, links, problems);
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
    return new BitbucketCloudPublisher(buildType, buildFeatureId, myExecutorServices, myLinks, params, myProblems);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Bitbucket Cloud";
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_USERNAME)))
          errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_USERNAME, "must be specified"));

        if (StringUtil.isEmptyOrSpaces(params.get(Constants.BITBUCKET_CLOUD_PASSWORD)))
          errors.add(new InvalidProperty(Constants.BITBUCKET_CLOUD_PASSWORD, "must be specified"));

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
    String vcsUrl = root.getProperty("url");
    Repository repository = null == vcsUrl ? null : GitRepositoryParser.parseRepository(vcsUrl);
    if (null == repository)
      throw new PublisherException("Cannot parse repository URL from VCS root " + root.getName());
    final String repoName = repository.repositoryName();
    String url = myDefaultApiUrl + "/2.0/repositories/" + repository.owner() + "/" + repoName;
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
          BitbucketCloudRepoInfo repoInfo = myGson.fromJson(json, BitbucketCloudRepoInfo.class);
          if (null == repoInfo)
            throw new HttpPublisherException("Bitbucket Cloud publisher has received a malformed response");
          if (null == repoInfo.slug || !repoInfo.slug.equals(repoName)) {
            throw new HttpPublisherException("No repository found");
          }
        }
      };

      HttpHelper.get(url, params.get(Constants.BITBUCKET_CLOUD_USERNAME), params.get(Constants.BITBUCKET_CLOUD_PASSWORD),
                     Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT, processor);
    } catch (Exception ex) {
      throw new PublisherException(String.format("Bitbucket Cloud publisher has failed to connect to %s/%s repository", repository.owner(), repository.repositoryName()), ex);
    }
  }

}
