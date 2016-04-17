package jetbrains.buildServer.commitPublisher.deveo;

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

class DeveoRepositoryParser {
  private static final Logger LOG = Logger.getInstance(DeveoRepositoryParser.class.getName());

  public static final Pattern URL_PATTERN = Pattern.compile(".+/projects/([^/]+)/repositories/(?:mercurial|git|subversion)/(.+?)/?$");

  @Nullable
  public static Repository parseRepository(@NotNull VcsRootInstance root) {
    String url = null;
    if ("jetbrains.git".equals(root.getVcsName()) || "svn".equals(root.getVcsName())) {
      url = root.getProperty("url");
    }
    if ("mercurial".equals(root.getVcsName())) {
      url = root.getProperty("repositoryPath");
    }
    return url == null ? null : parseRepository(url, root.getVcsName());
  }

  @Nullable
  private static Repository parseRepository(@NotNull String uri, @NotNull String repositoryType) {
    Matcher m = URL_PATTERN.matcher(uri);

    if (!m.matches()) {
      LOG.warn("Cannot parse " + repositoryType + " repository url " + uri);
      return null;
    }
    String owner = m.group(1);
    String repo = m.group(2);
    return new Repository(owner, repo);
  }
}
