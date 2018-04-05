package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitRepositoryParser {
  private static final Logger LOG = Logger.getInstance(GitRepositoryParser.class.getName());

  private static final Pattern GIT_URL_PATTERN = Pattern.compile("([a-zA-Z]+)://(?:[^:@/]+@)?[^:/]+(?::[0-9]+)?[:/]([^:]+)/([^/]+)/?");
  private static final Pattern PROTOCOL_PREFIX_PATTERN = Pattern.compile("[a-zA-Z]+://.+");
  private static final Pattern GIT_SCP_PATTERN = Pattern.compile("(?:[^:@/]+@)?[^:/]+:/?([^:]+)/([^/]+)/?");

  @Nullable
  public static Repository parseRepository(@NotNull String uri) {
    return parseRepository(uri, null);
  }

  @Nullable
  public static Repository parseRepository(@NotNull String uri, @Nullable String pathPrefix) {

    Matcher m = GIT_URL_PATTERN.matcher(uri);
    if (m.matches())
      if (m.group(1).toLowerCase().startsWith("http")) {
        return getRepositoryInfo(m.group(2), m.group(3), pathPrefix);
      } else {
        return getRepositoryInfo(m.group(2), m.group(3), "");
      }
    m = PROTOCOL_PREFIX_PATTERN.matcher(uri);
    if (!m.matches()) {
      m = GIT_SCP_PATTERN.matcher(uri);
      if (m.matches()) {
        return getRepositoryInfo(m.group(1), m.group(2), "");
      }
    }
    LOG.warn("Cannot parse Git repository url " + uri);
    return null;
  }

  private static Repository getRepositoryInfo(@NotNull String pathGroup, @NotNull String repoGroup, @Nullable String pathPrefix) {
    String userGroup = pathGroup;
    if (null != pathPrefix) {
      userGroup = stripPrefixTrimmingSlashes(userGroup, pathPrefix);
    } else {
      int lastSlash = userGroup.lastIndexOf("/");
      if (lastSlash > -1)
        userGroup = userGroup.substring(lastSlash + 1);
    }
    String repo = repoGroup;
    if (repo.endsWith(".git"))
      repo = repo.substring(0, repo.length() - 4);
    return new Repository(userGroup, repo);
  }

  private static String stripPrefixTrimmingSlashes(@NotNull final String str, @NotNull final String prefix) {
    String s = str.startsWith("/") ? str : "/" + str;
    String p = prefix.startsWith("/") ? prefix : "/" + prefix;
    if (!p.endsWith("/"))
      p = p + "/";
    if (s.startsWith(p))
      return s.substring(p.length());
    else
      return str;
  }
}
