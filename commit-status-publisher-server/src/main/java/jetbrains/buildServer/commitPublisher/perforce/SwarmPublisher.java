package jetbrains.buildServer.commitPublisher.perforce;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.perforce.SwarmPublisherSettings.*;

/**
 * @author kir
 */
class SwarmPublisher extends HttpBasedCommitStatusPublisher {

  private final WebLinks myLinks;

  public SwarmPublisher(@NotNull SwarmPublisherSettings swarmPublisherSettings,
                        @NotNull SBuildType buildType,
                        @NotNull String buildFeatureId,
                        @NotNull Map<String, String> params,
                        @NotNull CommitStatusPublisherProblems problems,
                        @NotNull WebLinks links) {
    super(swarmPublisherSettings, buildType, buildFeatureId, params, problems);
    myLinks = links;
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

  private void info(String message) {
    LoggerUtil.LOG.info(message);
  }
  private void debug(String message) {
    LoggerUtil.LOG.info(message);
  }

  private void publishIfNeeded(BuildPromotion build, @NotNull BuildRevision revision, @NotNull final String commentTemplate) throws PublisherException {

    if (!build.isPersonal()) return;

    final String changelistId = getChangelistId(build, revision);
    if (changelistId == null) return;

    IOGuard.allowNetworkCall(() -> {
      for (Long reviewId : getReviewIds(build, changelistId)) {
        addCommentToReview(build, reviewId, commentTemplate);
      }
    });
  }

  @NotNull
  private List<Long> getReviewIds(@NotNull BuildPromotion build, String changelistId) throws PublisherException {
    final String getReviewsUrl = StringUtil.removeTailingSlash(myParams.get(PARAM_URL)) + "/api/v9/reviews?fields=id&change[]=" + changelistId;
    try {
      final ReadReviewsProcessor processor = new ReadReviewsProcessor(build.toString());
      HttpHelper.get(getReviewsUrl, myParams.get(PARAM_USERNAME), myParams.get(PARAM_PASSWORD),
                     null, getConnectionTimeout(), getSettings().trustStore(), processor);

      return processor.getReviewIds();
    } catch (IOException e) {
      throw new PublisherException("Cannot get list of reviews from " + getReviewsUrl + ":" + e.getMessage(),  e);
    }
  }


  private void addCommentToReview(BuildPromotion build, @NotNull Long reviewId, @NotNull String comment) throws PublisherException {

    final String fullComment = "TeamCity: " + String.format(comment, getBuildMarkdownLink(build)) +
                               "\nConfiguration: " + myBuildType.getExtendedFullName();

    final String addCommentUrl = StringUtil.removeTailingSlash(myParams.get(PARAM_URL)) + "/api/v9/comments";
    String data = "topic=reviews/" + reviewId + "&body=" + StringUtil.encodeURLParameter(fullComment);
    try {
      HttpHelper.post(addCommentUrl, myParams.get(PARAM_USERNAME), myParams.get(PARAM_PASSWORD),
                      data, ContentType.APPLICATION_FORM_URLENCODED, null, getConnectionTimeout(), getSettings().trustStore(), new DefaultHttpResponseProcessor());
    } catch (IOException e) {
      throw new PublisherException("Cannot add comment to review " + reviewId + " at " + addCommentUrl + ":" + e.getMessage(),  e);
    }
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

  private class ReadReviewsProcessor implements HttpResponseProcessor {

    private final List<Long> myReviewIds = new ArrayList<>();;
    private final String myBuildInfo;

    private ReadReviewsProcessor(@NotNull String buildInfo) {
      myBuildInfo = buildInfo;
    }

    @Override
    public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
      if (response.getStatusCode() >= 400) {
        throw new HttpPublisherException(response.getStatusCode(), response.getStatusText(), "Cannot get list of related reviews");
      }

      // response = {"lastSeen":19,"reviews":[{"id":19}],"totalCount":1}
      debug("Reviews response for " + myBuildInfo + " = " + response.getContent() + " " + response.getStatusCode() + " " + response.getStatusText());

      try {
        final JsonNode jsonNode = new ObjectMapper().readTree(response.getContent());
        final ArrayNode reviews = (ArrayNode)jsonNode.get("reviews");

        if (reviews != null) {
          for (Iterator<JsonNode> it = reviews.elements(); it.hasNext(); ) {
            JsonNode element = it.next();
            myReviewIds.add(element.get("id").longValue());
          }
        }
        if (myReviewIds.size() > 0) {
          info(String.format("Found Perforce Swarm reviews %s for build %s", myReviewIds, myBuildInfo));
        }

      } catch (JsonProcessingException e) {
        throw new HttpPublisherException("Error parsing JSON response from Perforce Swarm: " + e.getMessage(), e);
      }
    }

    @NotNull
    public List<Long> getReviewIds() {
      return myReviewIds;
    }
  }



}
