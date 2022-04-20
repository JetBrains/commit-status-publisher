/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import com.google.gson.*;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.data.BitbucketCloudCommitBuildStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class BitbucketCloudPublisher extends HttpBasedCommitStatusPublisher {
  private String myBaseUrl = BitbucketCloudSettings.DEFAULT_API_URL;
  private final Gson myGson = new Gson();

  BitbucketCloudPublisher(@NotNull CommitStatusPublisherSettings settings,
                          @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                          @NotNull WebLinks links,
                          @NotNull Map<String, String> params,
                          @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems, links);
  }

  @NotNull
  public String toString() {
    return "bitbucketCloud";
  }

  @Override
  @NotNull
  public String getId() {
    return Constants.BITBUCKET_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion,
                             @NotNull BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return vote(buildPromotion, revision, BitbucketCloudBuildStatus.INPROGRESS, additionalTaskInfo.getComment());
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion,
                                       @NotNull BuildRevision revision,
                                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    BitbucketCloudBuildStatus targetStatus = additionalTaskInfo.isPromotionReplaced() ? BitbucketCloudBuildStatus.INPROGRESS : BitbucketCloudBuildStatus.STOPPED;
    return vote(buildPromotion, revision, targetStatus, additionalTaskInfo.getComment());
  }

  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    vote(build, revision, BitbucketCloudBuildStatus.INPROGRESS, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    BitbucketCloudBuildStatus status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.SUCCESSFUL : BitbucketCloudBuildStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    vote(build, revision, status, description);
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) throws
                                                                                                                                                                PublisherException {
    BitbucketCloudBuildStatus status;
    if (buildInProgress) {
      status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.INPROGRESS : BitbucketCloudBuildStatus.FAILED;
    } else {
      status = build.getBuildStatus().isSuccessful() ? BitbucketCloudBuildStatus.SUCCESSFUL : BitbucketCloudBuildStatus.FAILED;
    }
    String description = build.getStatusDescriptor().getText();
    if (user != null && comment != null) {
      description += " with a comment by " + user.getExtendedName() + ": \"" + comment + "\"";
    }
    vote(build, revision, status, description);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    vote(build, revision, buildInProgress ? BitbucketCloudBuildStatus.INPROGRESS : BitbucketCloudBuildStatus.SUCCESSFUL, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    vote(build, revision, BitbucketCloudBuildStatus.STOPPED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    vote(build, revision, BitbucketCloudBuildStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    BitbucketCloudCommitBuildStatus buildStatus = getCommitStatus(revision, removedBuild.getBuildTypeId());
    return getRevisionStatusForRemovedBuild(removedBuild, buildStatus);
  }

  RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, BitbucketCloudCommitBuildStatus buildStatus) {
    if (buildStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(buildStatus);
    boolean isSameBuild = StringUtil.areEqual(myLinks.getQueuedBuildUrl(removedBuild), buildStatus.url);
    return new RevisionStatus(event, buildStatus.description, isSameBuild);
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
      BitbucketCloudCommitBuildStatus buildStatus = getCommitStatus(revision, buildPromotion.getBuildTypeId());
      return getRevisionStatus(buildPromotion, buildStatus);
  }

  @Nullable
  private BitbucketCloudCommitBuildStatus getCommitStatus(@NotNull BuildRevision revision, @NotNull String buildTypeId) throws PublisherException {
    VcsRootInstance root = revision.getRoot();
    Repository repository = BitbucketCloudSettings.VCS_PROPERTIES_PARSER.parseRepository(root);
    if (repository == null) {
      throw new PublisherException("Can not define repository for BitBucket Cloud root" + root.getName());
    }
    String url = String.format("%s2.0/repositories/%s/%s/commit/%s/statuses/build/%s",
                               getBaseUrl(), repository.owner(), repository.repositoryName(), revision.getRevision(), buildTypeId);
    ResponseEntityProcessor<BitbucketCloudCommitBuildStatus> processor = new ResponseEntityProcessor<BitbucketCloudCommitBuildStatus>(BitbucketCloudCommitBuildStatus.class) {
      @Override
      protected boolean handleError(@NotNull HttpHelper.HttpResponse response) throws HttpPublisherException {
        int statusCode = response.getStatusCode();
        if (statusCode >= 400) {
          if (statusCode == 404) return false;
          throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error");
        }
        return true;
      }
    };
    return get(url, getUsername(), getPassword(), null, processor);
  }

  @Nullable
  RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable BitbucketCloudCommitBuildStatus commitStatus) {
    if (commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuild = StringUtil.areEqual(getViewUrl(buildPromotion), commitStatus.url);;
    return new RevisionStatus(event, commitStatus.description, isSameBuild);
  }

  @Nullable
  private Event getTriggeredEvent(@NotNull BitbucketCloudCommitBuildStatus buildStatus) {
    if (buildStatus.state == null) {
      LOG.warn("No Bitbucket Cloud build status is provided. Related event can not be calculated");
      return null;
    }
    BitbucketCloudBuildStatus status = BitbucketCloudBuildStatus.getByName(buildStatus.state);
    if (status == null) {
      LOG.warn(String.format("Unknown Bitbucket Cloud build status: \"%s\". Related event can not be calculated", buildStatus.state));
      return null;
    }
    switch (status) {
      case INPROGRESS:
        if (buildStatus.description == null) return null;
        return buildStatus.description.contains(DefaultStatusMessages.BUILD_QUEUED) ? Event.QUEUED :
               buildStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE) ? Event.REMOVED_FROM_QUEUE : null;
      case STOPPED:
      case FAILED:
      case SUCCESSFUL:
        return null;  // these statuses do not affect on further behaviour
      default:
        LOG.warn("No event is assosiated with BitBucket Cloud build status \"" + buildStatus.state + "\". Related event can not be defined");
    }
    return null;
  }

  private boolean vote(BuildPromotion buildPromotion, BuildRevision revision, BitbucketCloudBuildStatus status, String comment) throws PublisherException {
    final String url = getViewUrl(buildPromotion);
    BitbucketCloudCommitBuildStatus buildStatus = new BitbucketCloudCommitBuildStatus(buildPromotion.getBuildTypeId(), status.name(), getBuildName(buildPromotion), comment, url);
    final VcsRootInstance root = revision.getRoot();
    Repository repository = BitbucketCloudSettings.VCS_PROPERTIES_PARSER.parseRepository(root);
    if (repository == null) {
      throw new PublisherException(String.format("Bitbucket publisher has failed to parse repository URL from VCS root '%s'", root.getName()));
    }
    vote(revision.getRevision(), buildStatus, repository, LogUtil.describe(buildPromotion));
    return true;
  }

  private String getBuildName(BuildPromotion buildPromotion) {
    StringBuilder sb = new StringBuilder();
    if (buildPromotion.getBuildType() != null) {
      sb.append(buildPromotion.getBuildType().getFullName());
    }
    SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild != null) {
      sb.append(" #").append(queuedBuild.getOrderNumber());
    }
    return sb.toString();
  }

  private void vote(@NotNull SBuild build,
                    @NotNull BuildRevision revision,
                    @NotNull BitbucketCloudBuildStatus status,
                    @NotNull String comment) throws PublisherException {
    final VcsRootInstance root = revision.getRoot();
    Repository repository = BitbucketCloudSettings.VCS_PROPERTIES_PARSER.parseRepository(root);
    if (repository == null) {
      throw new PublisherException(String.format("Bitbucket publisher has failed to parse repository URL from VCS root '%s'", root.getName()));
    }
    BitbucketCloudCommitBuildStatus buildStatus = new BitbucketCloudCommitBuildStatus(build.getBuildPromotion().getBuildTypeId(), status.name(), getBuildName(build), comment,
                                                                                      getViewUrl(build)
    );
    vote(revision.getRevision(), buildStatus, repository, LogUtil.describe(build));
  }

  private void vote(@NotNull String commit, @NotNull BitbucketCloudCommitBuildStatus status, @NotNull Repository repository, @NotNull String buildDescription) {
    String data = myGson.toJson(status);
    LOG.debug(getBaseUrl() + " :: " + commit + " :: " + data);
    String url = getBaseUrl() + "2.0/repositories/" + repository.owner() + "/" + repository.repositoryName() + "/commit/" + commit + "/statuses/build";
    postJson(url, getUsername(), getPassword(), data, null, buildDescription);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    final int statusCode = response.getStatusCode();
    if (statusCode >= 400)
      throw new HttpPublisherException(statusCode, response.getStatusText(), parseErrorMessage(response));
  }

  @Nullable
  private String parseErrorMessage(@NotNull HttpHelper.HttpResponse response) {
    try {
      String str = response.getContent();
      if (str == null) {
        return null;
      }
      LOG.debug("Bitbucket Cloud response: " + str);
      JsonElement json = new JsonParser().parse(str);
      if (!json.isJsonObject())
        return null;
      JsonObject jsonObj = json.getAsJsonObject();
      JsonElement error = jsonObj.get("error");
      if (error == null || !error.isJsonObject())
        return null;

      final JsonObject errorObj = error.getAsJsonObject();
      JsonElement msg = errorObj.get("message");
      if (msg == null)
        return null;
      StringBuilder result = new StringBuilder(msg.getAsString());
      JsonElement fields = errorObj.get("fields");
      if (fields != null && fields.isJsonObject()) {
        result.append(". ");
        JsonObject fieldsObj = fields.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : fieldsObj.entrySet()) {
          result.append("Field '").append(e.getKey()).append("': ").append(e.getValue().getAsString());
        }
      }
      return result.toString();
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  @NotNull
  private String getBuildName(@NotNull SBuild build) {
    return build.getFullName() + " #" + build.getBuildNumber();
  }

  private String getBaseUrl() { return myBaseUrl;  }

  /**
   * Used for testing only at the moments
   * @param url - new base URL to replace the default one
   */
  void setBaseUrl(String url) { myBaseUrl = url; }

  private String getUsername() {
    return myParams.get(Constants.BITBUCKET_CLOUD_USERNAME);
  }

  private String getPassword() {
    return myParams.get(Constants.BITBUCKET_CLOUD_PASSWORD);
  }

}
