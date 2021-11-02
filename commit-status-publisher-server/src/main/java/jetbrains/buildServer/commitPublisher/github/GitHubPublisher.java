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

package jetbrains.buildServer.commitPublisher.github;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

class GitHubPublisher extends BaseCommitStatusPublisher {

  private final ChangeStatusUpdater myUpdater;

  GitHubPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                  @NotNull ChangeStatusUpdater updater,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
    myUpdater = updater;
  }

  @NotNull
  public String toString() {
    return "github";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, true);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    updateBuildStatus(build, revision, false);
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull final SBuild build, @NotNull final BuildRevision revision, final boolean buildInProgress) throws PublisherException {
    updateBuildStatus(build, revision, buildInProgress);
    return true;
  }

  public String getServerUrl() {
    return myParams.get(Constants.GITHUB_SERVER);
  }

  private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
    final ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(revision.getRoot(), getParams(build.getBuildPromotion()), this);

    if (!revision.getRoot().getVcsName().equals("jetbrains.git")) {
      LOG.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration");
      return;
    }

    if (isStarting) {
      h.changeStarted(revision, build);
    } else {
      h.changeCompleted(revision, build);
    }
  }


  @NotNull
  private Map<String, String> getParams(@NotNull BuildPromotion buildPromotion) {
    String context = getCustomContextFromParameter(buildPromotion);
    if (context == null)
      context = getDefaultContext(buildPromotion);
    Map<String, String> result = new HashMap<String, String>(myParams);
    result.put(Constants.GITHUB_CONTEXT, context);
    return result;
  }

  @NotNull
  String getDefaultContext(@NotNull BuildPromotion buildPromotion) {
    SBuildType buildType = buildPromotion.getBuildType();
    if (buildType != null) {
      String btName = removeMultiCharUnicodeAndTrim(buildType.getName());
      String prjName = removeMultiCharUnicodeAndTrim(buildType.getProject().getName());
      return String.format("%s (%s)", btName, prjName);
    } else {
      return "<Removed build configuration>";
    }
  }

  private String removeMultiCharUnicodeAndTrim(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c: s.toCharArray()) {
      if (c >= 0xd800L && c <= 0xdfffL || (c & 0xfff0) == 0xfe00 || c == 0x20e3 || c == 0x200d) {
        continue;
      }
      sb.append(c);
    }
    return sb.toString().trim();
  }

  @Nullable
  private String getCustomContextFromParameter(@NotNull BuildPromotion buildPromotion) {
    String value = buildPromotion.getBuildParameters().get(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM);
    if (value == null) {
      return null;
    }
    SBuild associatedBuild = buildPromotion.getAssociatedBuild();
    if (associatedBuild == null) {
      return null;
    }
    return associatedBuild.getValueResolver().resolve(value).getResult();
  }
}
