/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import java.util.Map;
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

  protected BaseCommitStatusPublisher(@NotNull CommitStatusPublisherSettings settings,
                                      @NotNull SBuildType buildType,@NotNull String buildFeatureId,
                                      @NotNull Map<String, String> params,
                                      @NotNull CommitStatusPublisherProblems problems) {
    mySettings = settings;
    myParams = params;
    myProblems = problems;
    myBuildType = buildType;
    myBuildFeatureId = buildFeatureId;
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
}
