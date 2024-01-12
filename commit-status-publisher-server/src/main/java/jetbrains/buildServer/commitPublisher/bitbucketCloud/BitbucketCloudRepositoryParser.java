

package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import jetbrains.buildServer.commitPublisher.GitRepositoryParser;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class BitbucketCloudRepositoryParser {
  private static final Pattern SSH_PATTERN = Pattern.compile("ssh://hg@bitbucket.org/([^/]+)/(.+)");
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser(true);

  @Nullable
  public Repository parseRepository(@NotNull VcsRoot root) {
    if ("jetbrains.git".equals(root.getVcsName())) {
      String url = root.getProperty("url");
      return url == null ? null : VCS_URL_PARSER.parseRepositoryUrl(url);
    }
    if ("mercurial".equals(root.getVcsName())) {
      String url = root.getProperty("repositoryPath");
      return url == null ? null : parseMercurialRepository(url);
    }
    return null;
  }

  @Nullable
  private Repository parseMercurialRepository(@NotNull String uri) {
    if (uri.startsWith("ssh")) {
      Matcher m = SSH_PATTERN.matcher(uri);
      if (!m.matches()) {
        LOG.warn("Cannot parse mercurial repository url " + uri);
        return null;
      }
      String owner = m.group(1).toLowerCase();
      String repo = m.group(2).toLowerCase();
      return new Repository(uri, owner, repo);
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
    String repo = path.substring(idx + 1);
    return new Repository(uri, owner, repo);
  }
}