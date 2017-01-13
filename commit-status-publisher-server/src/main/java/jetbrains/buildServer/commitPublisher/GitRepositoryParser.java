package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitRepositoryParser {
  private static final Logger LOG = Logger.getInstance(GitRepositoryParser.class.getName());
  //git@host:user/repo
  private static final Pattern SCP_PATTERN = Pattern.compile("git@[^:]+[:]([^/]+)/(.+)");
  //ssh://git@host/user/repo
  private static final Pattern SSH_PATTERN = Pattern.compile("ssh://(?:git@)?[^:]+(?::[0-9]+)?[:/]([^/:]+)/(.+)");

  @Nullable
  public static Repository parseRepository(@NotNull String uri) {
    if (uri.startsWith("git@") || uri.startsWith("ssh://")) {
      Matcher m = SCP_PATTERN.matcher(uri);
      if (!m.matches()) {
        m = SSH_PATTERN.matcher(uri);
        if (!m.matches()) {
          LOG.warn("Cannot parse Git repository url " + uri);
          return null;
        }
      }
      String userGroup = m.group(1);
      String repo = m.group(2);
      if (repo.endsWith(".git"))
        repo = repo.substring(0, repo.length() - 4);
      return new Repository(userGroup, repo);
    }

    URI url;
    try {
      url = new URI(uri);
    } catch (URISyntaxException e) {
      LOG.warn("Cannot parse Git repository url " + uri, e);
      return null;
    }


    String path = url.getPath();
    if (path == null) {
      LOG.warn("Cannot parse Git repository url " + uri + ", path is empty");
      return null;
    }

    String [] pathComponents = path.split("/");
    int l = pathComponents.length;
    if (l < 2) {
      LOG.warn("Cannot parse Git repository url " + uri);
      return null;
    }
    String owner = pathComponents[l-2];
    String repo = pathComponents[l-1];
    if (repo.endsWith(".git"))
      repo = repo.substring(0, repo.length() - 4);
    return new Repository(owner, repo);
  }
}
