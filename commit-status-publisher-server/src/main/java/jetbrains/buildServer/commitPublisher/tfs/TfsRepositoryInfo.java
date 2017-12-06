package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.serverSide.TeamCityProperties;
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
  private static final String[] TFS_HOSTED_DOMAINS = new String[]{"visualstudio.com"};
  private static final String TEAMCITY_TFS_HOSTED_DOMAINS = "teamcity.tfs.hosted.domains";
  private static final Pattern TFS_HOSTS_SEPARATOR = Pattern.compile(",");

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

    int lastSlash = path.lastIndexOf('/');
    String project = null;

    if (lastSlash >= 0) {
      final String lastPathSegment = path.substring(lastSlash + 1);
      if (!"defaultCollection".equalsIgnoreCase(lastPathSegment)) {
        String collection = path.substring(0, lastSlash);
        if (StringUtil.isNotEmpty(collection) || isHosted(server)) {
          project = lastPathSegment;
          path = collection;
        }
      }
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
  
  private static boolean isHosted(final String host) {
    for (String domain : getTfsHostedDomains()) {
      if (host.endsWith(domain)) return true;
    }
    return false;
  }

  private static String[] getTfsHostedDomains() {
    final String domainsList = TeamCityProperties.getPropertyOrNull(TEAMCITY_TFS_HOSTED_DOMAINS);
    if (StringUtil.isEmpty(domainsList)) {
      return TFS_HOSTED_DOMAINS;
    }

    return TFS_HOSTS_SEPARATOR.split(domainsList);
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
