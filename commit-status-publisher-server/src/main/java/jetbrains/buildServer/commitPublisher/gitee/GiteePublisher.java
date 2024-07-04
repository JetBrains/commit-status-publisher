

package jetbrains.buildServer.commitPublisher.gitee;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.userChanges.CanceledInfo;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GiteePublisher extends HttpBasedCommitStatusPublisher<GiteeBuildStatus> {

  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();


  GiteePublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull WebLinks links,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems, links);
  }

  @NotNull
  public String toString() {
    return "Gitee";
  }

  @Override
  @NotNull
  public String getId() {
    return GiteeConstants.GITEE_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion,
                             @NotNull BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return publishCommentIfNeeded(buildPromotion, revision, "build is queued", Event.QUEUED);
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    String comment = additionalTaskInfo.getComment();
    String commentTemplate = "build %s was removed from queue" + (StringUtil.isNotEmpty(comment) ? ": " + comment : "");
    if (additionalTaskInfo.getCommentAuthor() != null) {
      commentTemplate += " by " + additionalTaskInfo.getCommentAuthor().getDescriptiveName();
    }
    return publishCommentIfNeeded(buildPromotion, revision, commentTemplate, Event.REMOVED_FROM_QUEUE);
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    return publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **has started**", Event.STARTED);
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    String result = build.getBuildStatus().isSuccessful() ? "finished successfully" : "failed";
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **has " + result + "** : " + build.getStatusDescriptor().getText(), Event.FINISHED);
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) throws
                                                                                                                                                                PublisherException {
    String status;
    if (buildInProgress) {
      status = build.getBuildStatus().isSuccessful() ? "is running " : "has failed ";
    } else {
      status = build.getBuildStatus().isSuccessful() ? "has finished succesfully " : "has failed ";
    }
    String description = build.getStatusDescriptor().getText();
    if (user != null && comment != null) {
      description += " with a comment by " + user.getExtendedName() + ": \"" + comment + "\"";
    }
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **" + status + description + "** : " + build.getStatusDescriptor().getText(), Event.FINISHED);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s **was interrupted**: " + build.getStatusDescriptor().getText(), Event.INTERRUPTED);
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    final CanceledInfo canceledInfo = build.getCanceledInfo();
    if (canceledInfo != null) {
      LOG.debug(() -> "not publishing build failure, as build " + LogUtil.describe(build) + " is cancelled: " + canceledInfo);
      return false;
    }

    publishCommentIfNeeded(build.getBuildPromotion(), revision, "failure was detected in build %s: " + build.getStatusDescriptor().getText(), Event.FAILURE_DETECTED);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publishCommentIfNeeded(build.getBuildPromotion(), revision, "build %s was **marked as successful**: " + build.getStatusDescriptor().getText(), Event.MARKED_AS_SUCCESSFUL);
    return true;
  }

  private boolean publishCommentIfNeeded(
    @NotNull BuildPromotion buildPromotion,
    @NotNull BuildRevision revision,
    @NotNull String commentTemplate,
    @NotNull CommitStatusPublisher.Event status
  ) throws PublisherException {
    final String url = getViewUrl(buildPromotion);
    if (url == null) {
      LOG.warn(String.format("Can not build view URL for the build #%d. The build configuration was probably removed. Status \"%s\" won't be published",
                              buildPromotion.getId(), status.name()));
      return false;
    }
    final VcsRootInstance root = revision.getRoot();
    Repository repository = getRepositoryFromVcs(revision);
    Integer pullRequestNumber = getPullRequestNumber(buildPromotion, repository, root);
    if (pullRequestNumber == null) {
      if (!TeamCityProperties.getBoolean("teamcity.commitStatusPublisher.gitee.commitComments.enabled")) {
        LOG.warn("Comments for Commits are disabled");
        return false;
      }
    }
    final String fullComment = "TeamCity: " + buildMessage(buildPromotion, commentTemplate, url) +
                               "\nConfiguration: " + myBuildType.getExtendedFullName();

    publish(revision, fullComment, repository, LogUtil.describe(buildPromotion), pullRequestNumber);
    return true;
  }

  private static @Nullable Integer getPullRequestNumber(@NotNull BuildPromotion buildPromotion, Repository repository, VcsRootInstance root) throws PublisherException {
    if (repository == null) {
      throw new PublisherException(String.format("Gitee publisher has failed to parse repository URL from VCS root '%s'", root.getName()));
    }
    if (buildPromotion.getBranch() == null) {
      throw new PublisherException("No Branch name provided");
    }
    String branchName = buildPromotion.getBranch().getName();
    Integer pullRequestNumber = null;

    String regex = "pull/\\d+";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(branchName);

    if (matcher.matches()) {
      pullRequestNumber = Integer.parseInt(branchName.split("/")[1]);
    }
    return pullRequestNumber;
  }

  private String buildMessage(@NotNull BuildPromotion build, @NotNull String commentTemplate, @NotNull String url) {
    if (commentTemplate.contains("%s")) {
      return String.format(commentTemplate, getBuildMarkdownLink(build, url));
    }
    else {
      return commentTemplate + String.format(" ([view](%s))", url);
    }
  }

  @NotNull
  private String getBuildMarkdownLink(@NotNull BuildPromotion build, String url) {
    SBuild associatedBuild = build.getAssociatedBuild();
    SQueuedBuild queuedBuild = build.getQueuedBuild();
    if (associatedBuild != null) {
      return String.format("#[%s](%s)", associatedBuild.getBuildNumber(), url);
    }
    if (queuedBuild != null) {
      return String.format("#[%s](%s)", queuedBuild.getOrderNumber(), url);
    }
    return String.format("#[%d](%s)", build.getId(), url);
  }

  private void publish(@NotNull BuildRevision revision, @NotNull String fullComment, @NotNull Repository repository, @NotNull String buildDescription, Integer pullRequestNumber)
    throws PublisherException {
    final String commit = revision.getRevision();
    String url;
    if (pullRequestNumber == null) {
      url = getApiUrl(revision) + "/repos/" + repository.owner() + "/" + repository.repositoryName() + "/commits/" + commit + "/comments";
    } else {
      url = getApiUrl(revision) + "/repos/" + repository.owner() + "/" + repository.repositoryName() + "/pulls/" + pullRequestNumber + "/comments";
    }
    LOG.debug(url + " with comment " + fullComment);
    String body = "{ \"body\": \"" + fullComment + "\" }";
    postJson(url, getCredentials(revision.getRoot()), body, null, buildDescription);
  }

  private Repository getRepositoryFromVcs(@NotNull BuildRevision revision) {
    final VcsRootInstance root = revision.getRoot();
    String url = root.getProperty("url");
    if (url == null) return null;
    return VCS_URL_PARSER.parseRepositoryUrl(url);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400)
      if (statusCode == 404) {
        if ("{\"message\":\"Pull Request not found\"}".equals(response.getContent())) {
          throw new HttpPublisherException(statusCode, response.getStatusText(), "Pull Request not found. Branch name can not be \"pull/{number}\"");
        }
      }
    throw new HttpPublisherException(statusCode, response.getStatusText(), response.getContent());
  }

  protected String getApiUrl(@Nullable BuildRevision revision)  throws PublisherException {
    if (!StringUtil.isEmptyOrSpaces(myParams.get(GiteeConstants.GITEE_API_URL)))
      return HttpHelper.stripTrailingSlash(myParams.get(GiteeConstants.GITEE_API_URL));

    if (revision == null) {
      throw new PublisherException("Gitee API URL not set and no Build Revision provided");
    }
    VcsRootInstance root = revision.getRoot();
    if (root == null)
      throw new PublisherException("Vcs Root is null.");
    return getApiUrlFromVcsRootUrl(root.getProperty("url"));
    }


  @Nullable
  private HttpCredentials getCredentials(@NotNull VcsRootInstance root) throws PublisherException {
    return getSettings().getCredentials(myBuildType.getProject(), root, myParams);
  }
}