package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitRepositoryParser {
  private static final Logger LOG = Logger.getInstance(GitRepositoryParser.class.getName());
  private static final Pattern SSH_PATTERN = Pattern.compile("(?:ssh://)?git@[^:]+:([^/]+)/(.+)");

  @Nullable
  public static Repository parseRepository(@NotNull String uri) {
    if (uri.startsWith("git@") || uri.startsWith("ssh://")) {
      Matcher m = SSH_PATTERN.matcher(uri);
      if(!m.matches()) {
        LOG.warn("Cannot parse Git repository url " + uri);
        return null;
      }
      String userGroup = m.group(1);
      String repo = m.group(2);
      if (repo.endsWith(".git"))
        repo = repo.substring(0, repo.length() - 4);
      return new Repository(userGroup, repo);
    }

    URL url;
    try {
      url = new URL(uri);
    } catch (MalformedURLException e) {
      LOG.warn("Cannot parse Git repository url " + uri, e);
      return null;
    }

    String path = url.getPath();
    if (path == null) {
      LOG.warn("Cannot parse Git repository url " + uri + ", path is empty");
      return null;
    }

    if (path.startsWith("/"))
      path = path.substring(1);
    int idx = path.indexOf("/");
    if (idx <= 0) {
      LOG.warn("Cannot parse Git repository url " + uri);
      return null;
    }
    String owner = path.substring(0, idx);
    String repo = path.substring(idx + 1, path.length());
    if (repo.endsWith(".git"))
      repo = repo.substring(0, repo.length() - 4);
    return new Repository(owner, repo);
  }
}
