package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TfsRepositoryInfo {

  // Captures following groups: (server url) (project path) /_git/ (repository name)
  // Example: (http://localhost:81) (/tfs/collection) (/_git/) (git_project)
  private static final Pattern TFS_GIT_PROJECT_PATTERN = Pattern.compile(
    "(https?\\:\\/\\/[^\\/\\:]+(?:\\:\\d+)?)(\\/.+)?\\/_git\\/([^\\/]+)");

  // Cleanup pattern for TFS paths
  private static final Pattern TFS_PATH_PATTERN = Pattern.compile(
    "^(\\/DefaultCollection)?((\\/[^\\/]+)+?)?(\\/_.*)?$", Pattern.CASE_INSENSITIVE);

  private final String myServer;
  private final String myRepository;
  private final String myProject;

  TfsRepositoryInfo(@NotNull String server, @NotNull String repository, @Nullable String project) {
    myServer = server;
    myRepository = repository;
    myProject = project;
  }

  @Nullable
  public static TfsRepositoryInfo parse(@Nullable final String repositoryUrl) {
    if (StringUtil.isEmptyOrSpaces(repositoryUrl)) {
      return null;
    }

    final Matcher matcher = TFS_GIT_PROJECT_PATTERN.matcher(repositoryUrl.trim());
    if (!matcher.find()) {
      return null;
    }

    final String server = matcher.group(1);
    String path = StringUtil.notEmpty(matcher.group(2), StringUtil.EMPTY);
    String repository = matcher.group(3);

    // Cleanup TFS url paths
    if (StringUtil.isNotEmpty(path)) {
      path = TFS_PATH_PATTERN.matcher(path).replaceFirst("$2");
    }

    int lastSlash = path.lastIndexOf('/');
    final String project;
    if (lastSlash < 0) {
      project = null;
    } else {
      project = path.substring(lastSlash + 1);
      path = path.substring(0, lastSlash);
    }

    return new TfsRepositoryInfo(server + path, repository, project);
  }

  @NotNull
  public String getServer() {
    return myServer;
  }

  @NotNull
  public String getRepository() {
    return myRepository;
  }

  @NotNull
  public String getProject() {
    return StringUtil.notEmpty(myProject, myRepository);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder(myServer);
    if (StringUtil.isNotEmpty(myProject)) {
      builder.append('/').append(myProject);
    }
    builder.append("/_git/").append(myRepository);
    return builder.toString();
  }
}
