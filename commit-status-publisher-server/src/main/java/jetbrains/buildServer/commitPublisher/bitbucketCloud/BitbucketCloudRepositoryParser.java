package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.GitRepositoryParser;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BitbucketCloudRepositoryParser {
  private static final Logger LOG = Logger.getInstance(BitbucketCloudRepositoryParser.class.getName());
  private static final Pattern SSH_PATTERN = Pattern.compile("ssh://hg@bitbucket.org/([^/]+)/(.+)");

  @Nullable
  public static Repository parseRepository(@NotNull VcsRootInstance root) {
    if ("jetbrains.git".equals(root.getVcsName())) {
      String url = root.getProperty("url");
      return url == null ? null : GitRepositoryParser.parseRepository(url);
    }
    if ("mercurial".equals(root.getVcsName())) {
      String url = root.getProperty("repositoryPath");
      return url == null ? null : parseRepository(url);
    }
    return null;
  }

  @Nullable
  private static Repository parseRepository(@NotNull String uri) {
    if (uri.startsWith("ssh")) {
      Matcher m = SSH_PATTERN.matcher(uri);
      if (!m.matches()) {
        LOG.warn("Cannot parse mercurial repository url " + uri);
        return null;
      }
      String owner = m.group(1);
      String repo = m.group(2);
      return new Repository(owner, repo);
    }

    URL url;
    try {
      url = new URL(uri);
    } catch (MalformedURLException e) {
      LOG.warn("Cannot parse mercurial repository url " + uri, e);
      return null;
    }

    String path = url.getPath();
    if (path == null) {
      LOG.warn("Cannot parse mercurial repository url " + uri + ", path is empty");
      return null;
    }

    if (path.startsWith("/"))
      path = path.substring(1);
    int idx = path.indexOf("/");
    if (idx <= 0) {
      LOG.warn("Cannot parse mercurial repository url " + uri);
      return null;
    }
    String owner = path.substring(0, idx);
    String repo = path.substring(idx + 1, path.length());
    return new Repository(owner, repo);
  }
}
