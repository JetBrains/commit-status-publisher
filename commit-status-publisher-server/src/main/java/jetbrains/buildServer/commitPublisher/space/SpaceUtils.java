package jetbrains.buildServer.commitPublisher.space;

import jetbrains.buildServer.commitPublisher.GitRepositoryParser;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class SpaceUtils {
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  @NotNull
  public static Repository getRepositoryInfo(@NotNull VcsRoot root, @Nullable String projectKey) throws PublisherException {
    String url = root.getProperty("url");
    if (null == url) {
      throw new PublisherException("Cannot parse repository URL from VCS root (url not present) " + root.getName());
    }

    Repository repo = VCS_URL_PARSER.parseRepositoryUrl(url);
    if (repo != null) {
      if(StringUtil.isEmpty(projectKey))
        return repo;
      return new Repository(url, projectKey, repo.repositoryName());
    }

    url = StringUtil.removeTailingSlash(url);
    url = StringUtil.removeSuffix(url, ".git", true);
    int lastSlash = url.lastIndexOf('/');
    if (lastSlash == -1) {
      throw new PublisherException("Cannot parse repository URL from VCS root (incorrect format) " + root.getName());
    }
    String repoName = url.substring(lastSlash + 1);
    if (!StringUtil.isEmpty(projectKey)) {
      return new Repository(url, projectKey, repoName);
    }
    throw new PublisherException("A project key is neither provided nor can be derived from the repository URL " + url);
  }

  @NotNull
  public static SpaceConnectDescriber getConnectionData(@NotNull Map<String, String> params,
                                                        @NotNull OAuthConnectionsManager oAuthConnectionsManager,
                                                        @NotNull SProject project) {

    String credentialsType = params.get(Constants.SPACE_CREDENTIALS_TYPE);

    switch (credentialsType) {
      case Constants.SPACE_CREDENTIALS_CONNECTION:
        OAuthConnectionDescriptor oAuthConnectionDescriptor = oAuthConnectionsManager.findConnectionById(project, params.get(Constants.SPACE_CONNECTION_ID));
        if (oAuthConnectionDescriptor == null) {
          throw new IllegalArgumentException("Can't find JetBrains Space connection");
        }
        return new SpaceConnectDescriber(oAuthConnectionDescriptor);

      default:
        throw new IllegalArgumentException("Incorrect JetBrains Space credentials type");

    }
  }
}
