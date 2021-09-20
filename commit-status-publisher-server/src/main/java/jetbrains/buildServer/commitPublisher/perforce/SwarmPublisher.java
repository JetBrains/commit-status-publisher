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
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.perforce.SwarmPublisherSettings.*;

/**
 * @author kir
 */
class SwarmPublisher extends HttpBasedCommitStatusPublisher {

  public SwarmPublisher(@NotNull SwarmPublisherSettings swarmPublisherSettings,
                        @NotNull SBuildType buildType,
                        @NotNull String buildFeatureId,
                        @NotNull Map<String, String> params,
                        @NotNull CommitStatusPublisherProblems problems) {
    super(swarmPublisherSettings, buildType, buildFeatureId, params, problems);
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
    publishIfNeeded(build.getBuildPromotion(), revision);
    return true;
  }
  //
  //@Override
  //public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
  //  publishIfNeeded(build.getBuildPromotion());
  //  return true;
  //}
  //
  //@Override
  //public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
  //  publishIfNeeded(build.getBuildPromotion());
  //  return true;
  //}
  //
  //@Override
  //public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
  //  publishIfNeeded(build.getBuildPromotion());
  //  return true;
  //}
  //
  //@Override
  //public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
  //  publishIfNeeded(build.getBuildPromotion());
  //  return true;
  //}

  private void info(String message) {
    LoggerUtil.LOG.info(message);
  }
  private void debug(String message) {
    LoggerUtil.LOG.info(message);
  }

  private void publishIfNeeded(BuildPromotion build, @NotNull BuildRevision revision) throws PublisherException {

    if (!build.isPersonal()) return;

    final String changelistId = getChangelistId(build, revision);
    if (changelistId == null) return;

    List<Long> reviewIds = getReviewIds(build, changelistId);

    //reviewIds.thenAccept((reviews) -> {
    //  final String addCommentUrl = StringUtil.removeTailingSlash(myParams.get(PARAM_URL)) + "/api/v9/comments";
    //  HttpHelper.post(addCommentUrl, myParams.get(PARAM_USERNAME), myParams.get(PARAM_PASSWORD),
    //                 null, getConnectionTimeout(), getSettings().trustStore(), );
    //
    //  postJson(StringUtil.removeTailingSlash(myParams.get(PARAM_URL)) + "/");
    //});
  }

  @NotNull
  private List<Long> getReviewIds(BuildPromotion build, String changelistId) throws PublisherException {
    final String getReviewsUrl = StringUtil.removeTailingSlash(myParams.get(PARAM_URL)) + "/api/v9/reviews?fields=id&change[]=" + changelistId;
    try {
      final ReadReviewsProcessor processor = new ReadReviewsProcessor(build.toString());
      HttpHelper.get(getReviewsUrl, myParams.get(PARAM_USERNAME), myParams.get(PARAM_PASSWORD),
                     null, getConnectionTimeout(), getSettings().trustStore(), processor);

      return processor.getReviewIds();
    } catch (IOException e) {
      throw new PublisherException("Trouble getting the list of reviews from " + getReviewsUrl + ":" + e.getMessage(),  e);
    }
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
