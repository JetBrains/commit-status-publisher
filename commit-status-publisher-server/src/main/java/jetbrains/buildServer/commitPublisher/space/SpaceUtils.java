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

public class SpaceUtils {
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  @NotNull
  public static String getRepositoryName(VcsRoot root) throws PublisherException {
    String url = root.getProperty("url");
    if (null == url) {
      throw new PublisherException("Cannot parse repository URL from VCS root (url not present) " + root.getName());
    }

    Repository repo = VCS_URL_PARSER.parseRepositoryUrl(url);
    String repoName;
    if (null == repo) {
      url = StringUtil.removeTailingSlash(url);
      url = StringUtil.removeSuffix(url, ".git", true);
      int lastSlash = url.lastIndexOf('/');
      if (lastSlash == -1) {
        throw new PublisherException("Cannot parse repository URL from VCS root (incorrect format) " + root.getName());
      }
      repoName = url.substring(lastSlash + 1);
    } else {
      repoName = repo.repositoryName();
    }

    return repoName;
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
