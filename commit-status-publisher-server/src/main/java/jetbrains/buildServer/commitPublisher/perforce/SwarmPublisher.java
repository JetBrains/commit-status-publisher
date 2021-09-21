package jetbrains.buildServer.commitPublisher.perforce;

import java.util.List;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.HttpBasedCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.*;
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
    // So far, process only builds with shelved changelists (by default personal builds with patches are ignored)
    return buildPromotion.isPersonal();
  }

  public boolean buildQueued(@NotNull SQueuedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s is queued");
    return true;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s has started");
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s has finished: " + build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "build %s was interrupted: " + build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publishIfNeeded(build.getBuildPromotion(), revision, "failure was detected in build %s: " + build.getStatusDescriptor().getText());
    return true;
  }

  private void publishIfNeeded(BuildPromotion build, @NotNull BuildRevision revision, @NotNull final String commentTemplate) throws PublisherException {

    if (!build.isPersonal()) return;

    final String changelistId = getChangelistId(build, revision);
    if (changelistId == null) return;

    IOGuard.allowNetworkCall(() -> {
      final List<Long> reviewIds = mySwarmClient.getReviewIds(changelistId, "build " + build);
      for (Long reviewId : reviewIds) {

        final String fullComment = "TeamCity: " + String.format(commentTemplate, getBuildMarkdownLink(build)) +
                                   "\nConfiguration: " + myBuildType.getExtendedFullName();

        mySwarmClient.addCommentToReview(reviewId, fullComment, "build " + build);
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

  @Nullable
  private String getChangelistId(@NotNull BuildPromotion promotion, @NotNull BuildRevision revision) {
    return promotion.getParameterValue("vcsRoot." + revision.getRoot().getExternalId() + ".shelvedChangelist");
  }

}
