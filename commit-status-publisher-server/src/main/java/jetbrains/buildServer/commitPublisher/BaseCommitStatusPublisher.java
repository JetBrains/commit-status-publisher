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

package jetbrains.buildServer.commitPublisher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public abstract class BaseCommitStatusPublisher implements CommitStatusPublisher {

  public static final int DEFAULT_CONNECTION_TIMEOUT = 10000;
  public static final String CONNECTION_TIMEOUT_PARAM = "commitStatusPublisher.connectionTimeout";
  protected final Map<String, String> myParams;
  private int myConnectionTimeout;
  protected CommitStatusPublisherProblems myProblems;
  protected SBuildType myBuildType;
  private final String myBuildFeatureId;
  private final CommitStatusPublisherSettings mySettings;
  protected final WebLinks myLinks;

  protected BaseCommitStatusPublisher(@NotNull CommitStatusPublisherSettings settings,
                                      @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                                      @NotNull Map<String, String> params,
                                      @NotNull CommitStatusPublisherProblems problems,
                                      @NotNull WebLinks links) {
    mySettings = settings;
    myParams = params;
    myProblems = problems;
    myBuildType = buildType;
    myBuildFeatureId = buildFeatureId;
    myLinks = links;
    myConnectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    if (buildType instanceof BuildTypeEx) {
      String strTimeout = ((BuildTypeEx)buildType).getInternalParameterValue(CONNECTION_TIMEOUT_PARAM, "");
      if (!StringUtil.isEmpty(strTimeout)) {
        try {
          myConnectionTimeout = Integer.parseInt(strTimeout);
        } catch (NumberFormatException ex) {
          LOG.warnAndDebugDetails("Failure to parse connection timeout value " + strTimeout, ex);
        }
      }
    }
  }

  public boolean buildQueued(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return false;
  }

  public boolean buildRemovedFromQueue(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision, @NotNull AdditionalTaskInfo additionalTaskInfo) throws PublisherException {
    return false;
  }

  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    return false;
  }

  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    return false;
  }

  public boolean buildCommented(@NotNull SBuild build, @NotNull BuildRevision revision, @Nullable User user, @Nullable String comment, boolean buildInProgress)
    throws PublisherException {
    return false;
  }

  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    return false;
  }

  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    return false;
  }

  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    return false;
  }

  protected int getConnectionTimeout() {
    return myConnectionTimeout;
  }

  public void setConnectionTimeout(int timeout) {
    myConnectionTimeout = timeout;
  }

  @Override
  public boolean publish(BuildRevision revision, CommonBuildStatus status) throws PublisherException {
    return false;
  }

  @Nullable
  public String getVcsRootId() {
    return myParams.get(Constants.VCS_ROOT_ID_PARAM);
  }

  @NotNull
  public CommitStatusPublisherSettings getSettings() {
    return mySettings;
  }

  public boolean isPublishingForRevision(@NotNull final BuildRevision revision) {
    VcsRoot vcsRoot = revision.getRoot();
    return getSettings().isPublishingForVcsRoot(vcsRoot);
  }

  public boolean isEventSupported(Event event) {
    return mySettings.isEventSupported(event, myBuildType, myParams);
  }

  @Override
  public boolean isAvailable(@NotNull BuildPromotion buildPromotion) {
    return !isPersonalBuildWithPatch(buildPromotion);
  }

  private boolean isPersonalBuildWithPatch(@NotNull BuildPromotion buildPromotion) {
    if (buildPromotion.isPersonal()) {
      for(SVcsModification change: buildPromotion.getPersonalChanges()) {
        if (change.isPersonal())
          return true;
      }
    }
    return false;
  }

  @NotNull
  public SBuildType getBuildType() { return myBuildType; }

  @NotNull
  public String getBuildFeatureId() { return myBuildFeatureId; }

  public CommitStatusPublisherProblems getProblems() {return myProblems; }

  @Override
  public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    return null;
  }

  @Override
  public CommonBuildStatus getLatestInformativeBuildStatusForPromotion(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
    return null;
  }

  protected <T> Optional<T> getBuildStatusFromStorage(@NotNull String buildName, @NotNull BuildRevision revision, @NotNull Function<String, T> unmarshaller, Predicate<T> predicate) {
    CustomDataStorage storage = getStatusHistoryDataStorage();
    if (storage == null) {
      LOG.debug(String.format("No storage is provided for publisher %s. Status won't be saved locally", getClass().getSimpleName()));
      return Optional.empty();
    }
    Map<String, String> statuses = storage.getValues();
    if (statuses == null) {
      LOG.debug("No previously published statuses is stored in storage. Can not find informative status");
      return Optional.empty();
    }
    final String keyPrefix = getStorageKeyPrefix(buildName, revision.getRevision());
    return statuses.entrySet().stream()
                                                            .filter(entry -> entry.getKey().startsWith(keyPrefix))
                                                            .sorted(Comparator.comparing(this::getDateFromStorageKey, Comparator.reverseOrder()))
                                                            .map(entry -> unmarshaller.apply(entry.getValue()))
                                                            .filter(predicate)
                                                            .findFirst();
  }

  @Override
  public RevisionStatus getRevisionStatusForRemovedBuild(@NotNull SQueuedBuild removedBuild, @NotNull BuildRevision revision) throws PublisherException {
    return null;
  }

  protected CustomDataStorage getStatusHistoryDataStorage() {
    return null;
  }

  @NotNull
  protected Set<String> getPossibleViewUrls(@NotNull BuildPromotion buildPromotion) {
    Set<String> result = new HashSet<>();
    SBuild build = ((BuildPromotionEx)buildPromotion).getRealOrDummyBuild();
    result.add(myLinks.getViewResultsUrl(build));
    SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    String rootUrl = myLinks.getRootUrlByProjectExternalId(buildPromotion.getProjectExternalId());
    result.add(rootUrl);
    if (queuedBuild != null) {
      result.add(myLinks.getQueuedBuildUrl(queuedBuild));
    } else {
      result.add((rootUrl.endsWith("/") ? rootUrl.substring(0, rootUrl.length()-1) : rootUrl) + "/viewQueued.html?itemId=" + buildPromotion.getId());  // preferable to mock QueuedBuild class and call mymyLinks.getQueuedBuildUrl(...) method
    }
    if (buildPromotion.getBuildType() != null) {
      result.add(myLinks.getConfigurationHomePageUrl(buildPromotion.getBuildType()));
    }
    return result;
  }

  protected String getViewUrl(@NotNull BuildPromotion buildPromotion) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      return myLinks.getViewResultsUrl(build);
    }
    SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild != null) {
      return myLinks.getQueuedBuildUrl(queuedBuild);
    }
    return buildPromotion.getBuildType() != null ? myLinks.getConfigurationHomePageUrl(buildPromotion.getBuildType()) :
           myLinks.getRootUrlByProjectExternalId(buildPromotion.getProjectExternalId());
  }

  protected String getStorageKeyPrefix(@NotNull String buildName, @NotNull String revision) {
    return String.format("%s#%s@", buildName, revision);
  }

  protected LocalDateTime getDateFromStorageKey(Map.Entry<String, String> entry) {
    String key = entry.getKey();
    int dateStart = key.indexOf('@') + 1;
    return LocalDateTime.parse(key.substring(dateStart), DateTimeFormatter.ISO_DATE_TIME);
  }

  protected void storeStatusInHistory(String buildName, String revision, String message) {
    CustomDataStorage storage = getStatusHistoryDataStorage();
    if (storage == null) return;

    String now = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
    storage.putValue(getStorageKeyPrefix(buildName, revision) + now, message);
  }
}
