

package jetbrains.buildServer.commitPublisher.gitea;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaPublishCommitStatus;
import jetbrains.buildServer.commitPublisher.gitea.data.GiteaReceiveCommitStatus;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GiteaPublisher extends HttpBasedCommitStatusPublisher<GiteaBuildStatus> {

  private static final String BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE = "commitStatusPublisher.gitea.buildNumberToName.enabled";
  private final Gson myGson = new Gson();
  private static final GitRepositoryParser VCS_URL_PARSER = new GitRepositoryParser();

  private final CommitStatusesCache<GiteaReceiveCommitStatus> myStatusesCache;

  GiteaPublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull WebLinks links,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems,
                 @NotNull CommitStatusesCache<GiteaReceiveCommitStatus> statusesCache) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    myStatusesCache = statusesCache;
  }

  @NotNull
  public String toString() {
    return "Gitea";
  }

  @Override
  @NotNull
  public String getId() {
    return GiteaConstants.GITEA_PUBLISHER_ID;
  }

  @Override
  public boolean buildQueued(@NotNull BuildPromotion buildPromotion,
                             @NotNull BuildRevision revision,
                             @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return publish(buildPromotion, revision, GiteaBuildStatus.PENDING, additionalTaskInfo.getComment());
  }

  @Override
  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion,
                                       @NotNull BuildRevision revision,
                                       @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return publish(buildPromotion, revision, GiteaBuildStatus.FAILURE, additionalTaskInfo.getComment());
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build.getBuildPromotion(), revision, GiteaBuildStatus.PENDING, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    GiteaBuildStatus status = build.getBuildStatus().isSuccessful() ? GiteaBuildStatus.SUCCESS : GiteaBuildStatus.FAILURE;
    String description = build.getStatusDescriptor().getText();
    publish(build.getBuildPromotion(), revision, status, description);
    return true;
  }

  @Override
  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress) throws
                                                                                                                                                                PublisherException {
    GiteaBuildStatus status;
    if (buildInProgress) {
      status = build.getBuildStatus().isSuccessful() ? GiteaBuildStatus.PENDING : GiteaBuildStatus.FAILURE;
    } else {
      status = build.getBuildStatus().isSuccessful() ? GiteaBuildStatus.SUCCESS : GiteaBuildStatus.FAILURE;
    }
    String description = build.getStatusDescriptor().getText();
    if (user != null && comment != null) {
      description += " with a comment by " + user.getExtendedName() + ": \"" + comment + "\"";
    }
    publish(build.getBuildPromotion(), revision, status, description);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build.getBuildPromotion(), revision, GiteaBuildStatus.ERROR, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build.getBuildPromotion(), revision, GiteaBuildStatus.FAILURE, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build.getBuildPromotion(), revision, buildInProgress ? GiteaBuildStatus.PENDING : GiteaBuildStatus.SUCCESS, DefaultStatusMessages.BUILD_MARKED_SUCCESSFULL);
    return true;
  }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    GiteaReceiveCommitStatus buildStatus = getLatestCommitStatusForBuild(revision, getBuildName(buildPromotion));
    return getRevisionStatus(buildPromotion, buildStatus);
  }

  @Nullable
  private RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @Nullable GiteaReceiveCommitStatus commitStatus) {
    if (commitStatus == null) {
      return null;
    }
    Event event = getTriggeredEvent(commitStatus);
    boolean isSameBuildType = StringUtil.areEqual(buildPromotion.getBuildType().getFullName(), commitStatus.context);
    return new RevisionStatus(event, commitStatus.description, isSameBuildType, getBuildIdFromViewUrl(commitStatus.target_url));
  }

  private GiteaReceiveCommitStatus getLatestCommitStatusForBuild(@NotNull BuildRevision revision, @NotNull String buildName) throws PublisherException {
    Repository repository = getRepositoryFromVcs(revision);
    if (repository == null)
      throw new PublisherException("Could not parse Gitea repository URL");
    AtomicReference<PublisherException> exception = new AtomicReference<>(null);
    GiteaReceiveCommitStatus statusFromCache = myStatusesCache.getStatusFromCache(revision, buildName, () -> {
      try {
        GiteaReceiveCommitStatus[] commitStatuses = loadCommitStatuses(repository, revision);
        return Arrays.asList(commitStatuses);
      } catch (PublisherException e) {
        exception.set(e);
        return Collections.emptyList();
      }
    }, status -> status.context);

    if (exception.get() != null) {
      throw exception.get();
    }
    return statusFromCache;
  }


  private GiteaReceiveCommitStatus[] loadCommitStatuses(Repository repository, BuildRevision revision) throws PublisherException {
    final String baseUrl = String.format("%s/repos/%s/%s/statuses/%s",
                                         getApiUrl(revision), repository.owner(), repository.repositoryName(), revision.getRevision());
    ResponseEntityProcessor<GiteaReceiveCommitStatus[]> processor = new ResponseEntityProcessor<>(GiteaReceiveCommitStatus[].class);
    GiteaReceiveCommitStatus[] commitStatuses = get(baseUrl, getCredentials(revision.getRoot()), null, processor);
    if (commitStatuses == null || commitStatuses.length == 0) {
      return new GiteaReceiveCommitStatus[0];
    }
    return commitStatuses;
  }


  @Nullable
  private Event getTriggeredEvent(@NotNull GiteaReceiveCommitStatus buildStatus) {
    if (buildStatus.status == null) {
      LOG.warn("No Gitea build status is provided. Related event can not be calculated");
      return null;
    }
    GiteaBuildStatus
      status = GiteaBuildStatus.getByName(buildStatus.status);
    if (status == null) {
      LOG.warn(String.format("Unknown Gitea build status: \"%s\". Related event can not be calculated", buildStatus.status));
      return null;
    }
    switch (status) {
      case PENDING:
        if (buildStatus.description == null) return null;
        return buildStatus.description.contains(DefaultStatusMessages.BUILD_QUEUED) ? Event.QUEUED :
               buildStatus.description.contains(DefaultStatusMessages.BUILD_STARTED) ? Event.STARTED :
               null;
      case ERROR:
        return (buildStatus.description != null && buildStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE)) ? Event.REMOVED_FROM_QUEUE :
               (buildStatus.description != null && buildStatus.description.contains(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED)) ? Event.REMOVED_FROM_QUEUE :
               null;
      case FAILURE:
      case SUCCESS:
        return null;  // these statuses do not affect on further behaviour
      default:
        LOG.warn("No event is assosiated with Gitea build status \"" + buildStatus.status + "\". Related event can not be defined");
    }
    return null;
  }

  @NotNull
  private GiteaPublishCommitStatus getBuildStatus(@NotNull BuildPromotion promotion,
                                                         @NotNull GiteaBuildStatus status,
                                                         @NotNull String comment,
                                                         @NotNull String url) {
    String buildName = getBuildName(promotion);
    return new GiteaPublishCommitStatus(buildName, comment, status.getName(), url);
  }

  @NotNull
  private String getBuildName(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType == null) return buildPromotion.getBuildTypeExternalId();

    final boolean isQueuedEnabled = getSettings().isPublishingQueuedStatusEnabled(buildType);
    if (isQueuedEnabled) {
      return buildType.getFullName();
    }

    final boolean includeBuildNumberToName = buildType instanceof BuildTypeEx && ((BuildTypeEx)buildType).getBooleanInternalParameterOrTrue(BUILD_NUMBER_IN_STATUS_NAME_FEATURE_TOGGLE);
    SBuild build = buildPromotion.getAssociatedBuild();
    StringBuilder sb = new StringBuilder(buildType.getFullName());
    if (includeBuildNumberToName && build != null) {
      sb.append(" #").append(build.getBuildNumber());
    }
    return sb.toString();
  }

  private boolean publish(
    @NotNull BuildPromotion buildPromotion,
    @NotNull BuildRevision revision,
    @NotNull GiteaBuildStatus status,
    @NotNull String comment
  ) throws PublisherException {
    final String url = getViewUrl(buildPromotion);
    if (url == null) {
      LOG.warn(String.format("Can not build view URL for the build #%d. The build configuration was probably removed. Status \"%s\" won't be published",
                              buildPromotion.getId(), status.name()));
      return false;
    }
    final VcsRootInstance root = revision.getRoot();
    Repository repository = getRepositoryFromVcs(revision);
    if (repository == null) {
      throw new PublisherException(String.format("Gitea publisher has failed to parse repository URL from VCS root '%s'", root.getName()));
    }

    GiteaPublishCommitStatus buildStatus = getBuildStatus(buildPromotion, status, comment, url);
    publish(revision, buildStatus, repository, LogUtil.describe(buildPromotion));
    myStatusesCache.removeStatusFromCache(revision, buildKey(buildPromotion));
    return true;
  }

  private void publish(@NotNull BuildRevision revision, @NotNull GiteaPublishCommitStatus status, @NotNull Repository repository, @NotNull String buildDescription)
    throws PublisherException {
    final String commit = revision.getRevision();
    String data = myGson.toJson(status);
    LOG.debug(getApiUrl(revision) + " :: " + commit + " :: " + data);
    String url = getApiUrl(revision) + "/repos/" + repository.owner() + "/" + repository.repositoryName() + "/statuses/" + commit;
    postJson(url, getCredentials(revision.getRoot()), data, null, buildDescription);
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
      throw new HttpPublisherException(statusCode, response.getStatusText(), parseErrorMessage(response));
  }

  @Nullable
  private String parseErrorMessage(@NotNull HttpHelper.HttpResponse response) {
    try {
      String str = response.getContent();
      if (str == null) {
        return null;
      }
      LOG.debug("Gitea response: " + str);
      JsonElement json = JsonParser.parseString(str);
      if (!json.isJsonObject())
        return null;
      JsonObject jsonObj = json.getAsJsonObject();
      JsonElement error = jsonObj.get("errors");
      if (error == null || !error.isJsonObject())
        return null;

      final JsonObject errorObj = error.getAsJsonObject();
      JsonElement msg = errorObj.get("message");
      if (msg == null)
        return null;
      StringBuilder result = new StringBuilder(msg.getAsString());
      return result.toString();
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  protected String getApiUrl(@Nullable BuildRevision revision)  throws PublisherException {
    if (!StringUtil.isEmptyOrSpaces(myParams.get(GiteaConstants.GITEA_API_URL)))
      return HttpHelper.stripTrailingSlash(myParams.get(GiteaConstants.GITEA_API_URL));

    if (revision == null) {
      throw new PublisherException("Gitea API URL not set and no Build Revision provided");
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

  @NotNull
  private static String buildKey(@NotNull BuildPromotion promotion) {
    if (promotion.isPersonal()) {
      SBuildType buildType = promotion.getBuildType();
      if (buildType != null)
        return buildType.getBuildTypeId();
    }
    return promotion.getBuildTypeId();
  }


}