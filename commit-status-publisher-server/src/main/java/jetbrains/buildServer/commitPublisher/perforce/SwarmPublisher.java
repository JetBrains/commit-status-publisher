package jetbrains.buildServer.commitPublisher.perforce;

import java.util.List;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.HttpBasedCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.perforce.SwarmPublisherSettings.ID;

/**
 * @author kir
 */
class SwarmPublisher extends HttpBasedCommitStatusPublisher {

  private final WebLinks myLinks;
  private final SwarmClient mySwarmClient;

  public SwarmPublisher(@NotNull SwarmPublisherSettings swarmPublisherSettings,
                        @NotNull SBuildType buildType,
                        @NotNull String buildFeatureId,
                        @NotNull Map<String, String> params,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull WebLinks links) {
    super(swarmPublisherSettings, buildType, buildFeatureId, params, problems);
    myLinks = links;

    mySwarmClient = new SwarmClient(params, getConnectionTimeout(), swarmPublisherSettings.trustStore());
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String toString() {
    return "perforceSwarm";
  }

  @Override
  public boolean isAvailable(@NotNull BuildPromotion buildPromotion) {
    // Process both ordinary builds and personal builds with shelved changelists
    return true;
  }

  @Nullable
  private String getChangelistId(@NotNull BuildPromotion promotion, @NotNull BuildRevision revision) {
    String shelvedChangelistId = promotion.getParameterValue("vcsRoot." + revision.getRoot().getExternalId() + ".shelvedChangelist");
    if (shelvedChangelistId == null) {
      shelvedChangelistId = revision.getRevisionDisplayName();
    }
    return shelvedChangelistId;
  }

  public boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s is queued");
    return true;
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull SQueuedBuild build,
                                       @NotNull BuildRevision revision,
                                       @Nullable User user,
                                       @Nullable String comment) throws PublisherException {
    if (comment != null && comment.contains("Build started")) {
      return true;
    }
    String commentTemplate = "build %s is removed from queue: " + comment;
    if (user != null) {
      commentTemplate += " by " + user.getDescriptiveName();
    }
    publishIfNeeded(build.getBuildPromotion(), revision, commentTemplate);
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s **has started**");
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    String result = build.getBuildStatus().isSuccessful() ? "finished successfully" : "failed";
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s **has " + result + "** : " + build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s **was interrupted**: " + build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "failure was detected in build %s: " + build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s was **marked as successful**: " + build.getStatusDescriptor().getText());
    return true;
  }

  private void publishIfNeeded(BuildPromotion build, @NotNull BuildRevision revision, @NotNull final String commentTemplate) throws PublisherException {

    final String changelistId = getChangelistId(build, revision);
    if (changelistId == null) return;

    final SBuildType buildType = build.getBuildType();
    if (buildType == null) return;

    mySwarmClient.setConnectionTimeout(getConnectionTimeout());

    IOGuard.allowNetworkCall(() -> {
      final String buildInfo = "build [id=" + build.getId() + "] in " + buildType.getExtendedFullName();

      final List<Long> reviewIds = mySwarmClient.getReviewIds(changelistId, buildInfo);
      for (Long reviewId : reviewIds) {

        final String fullComment = "TeamCity: " + String.format(commentTemplate, getBuildMarkdownLink(build)) +
                                   "\nConfiguration: " + myBuildType.getExtendedFullName();

        mySwarmClient.addCommentToReview(reviewId, fullComment, buildInfo);
      }
    });
  }

  @NotNull
  private String getBuildMarkdownLink(@NotNull BuildPromotion build) {
    SBuild associatedBuild = build.getAssociatedBuild();
    SQueuedBuild queuedBuild = build.getQueuedBuild();
    String url = associatedBuild != null ? myLinks.getViewResultsUrl(associatedBuild) :
                     queuedBuild != null ? myLinks.getQueuedBuildUrl(queuedBuild) :
                     myLinks.getRootUrlByProjectExternalId(build.getProjectExternalId()) + "/build/" + build.getId();
    if (associatedBuild != null) {
      return String.format("#[%s](%s)", associatedBuild.getBuildNumber(), url);
    }
    if (queuedBuild != null) {
      return String.format("#[%s](%s)", queuedBuild.getOrderNumber(), url);
    }
    return String.format("#[%d](%s)", build.getId(), url);
  }

}
