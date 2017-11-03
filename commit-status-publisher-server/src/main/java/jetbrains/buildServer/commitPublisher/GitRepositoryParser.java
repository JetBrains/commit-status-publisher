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
  private static final Pattern SCP_PATTERN = Pattern.compile("\\w+@[^:]+[:]([^/]+)/(.+)");
  private static final Pattern SCP_PATTERN_SLASHES = Pattern.compile("\\w+@[^:]+[:](.+)/([^/]+)");
  //ssh://git@host/user/repo
  private static final Pattern SSH_PATTERN = Pattern.compile("ssh://(?:\\w+@)?[^:]+(?::[0-9]+)?[:/]([^/:]+)/(.+)");
  private static final Pattern SSH_PATTERN_SLASHES = Pattern.compile("ssh://(?:\\w+@)?[^:/]+(?::[0-9]+)?[:/]([^:]+)/([^/]+)");

  @Nullable
  public static Repository parseRepository(@NotNull String uri) {
    return parseRepository(uri, null);
  }

  @Nullable
  public static Repository parseRepository(@NotNull String uri, @Nullable String pathPrefix) {
    if (uri.matches("^\\w+@.+") || uri.startsWith("ssh://")) {
      Matcher m = (null == pathPrefix ? SCP_PATTERN : SCP_PATTERN_SLASHES).matcher(uri);
      if (!m.matches()) {
        m = (null == pathPrefix ? SSH_PATTERN : SSH_PATTERN_SLASHES).matcher(uri);
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
    if (path != null) {
      String repo;
      String owner;
      int lastSlash = path.lastIndexOf("/");
      if (lastSlash > 0) {
        repo = path.substring(lastSlash + 1);
        if (repo.endsWith(".git"))
          repo = repo.substring(0, repo.length() - 4);
        int ownerStart = pathPrefix == null ? path.lastIndexOf("/", lastSlash - 1) : pathPrefix.length();
        if (ownerStart >= 0) {
          owner = path.substring(ownerStart, lastSlash);
          if (owner.startsWith("/"))
            owner = owner.substring(1);
          return new Repository(owner, repo);
        }
      }
    }
    LOG.warn("Cannot parse Git repository url " + uri);
    return null;
  }
}
