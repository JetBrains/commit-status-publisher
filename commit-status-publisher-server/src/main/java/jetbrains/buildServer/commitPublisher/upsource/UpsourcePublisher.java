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

package jetbrains.buildServer.commitPublisher.upsource;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class UpsourcePublisher extends HttpBasedCommitStatusPublisher<UpsourceStatus> {

  private final VcsModificationHistory myVcsHistory;
  private final Gson myGson = new Gson();
  private static final Pattern TEAMCITY_SVN_REVISION_PATTERN = Pattern.compile("([^\\|]+\\|)?([0-9]+)(_.+)?");


  UpsourcePublisher(@NotNull CommitStatusPublisherSettings settings,
                    @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                    @NotNull VcsModificationHistory vcsHistory,
                    @NotNull WebLinks links, @NotNull Map<String, String> params,
                    @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems, links);
    myVcsHistory = vcsHistory;
  }

  @NotNull
  @Override
  public String toString() {
    return "upsource";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.UPSOURCE_PUBLISHER_ID;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, UpsourceStatus.IN_PROGRESS, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    UpsourceStatus status = build.getBuildStatus().isSuccessful() ? UpsourceStatus.SUCCESS : UpsourceStatus.FAILED;
    String description = build.getStatusDescriptor().getText();
    publish(build, revision, status, description);
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, UpsourceStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, UpsourceStatus.FAILED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build, revision, buildInProgress ? UpsourceStatus.IN_PROGRESS : UpsourceStatus.SUCCESS, "Build marked as successful");
    return true;
  }

  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull UpsourceStatus status,
                       @NotNull String description) throws PublisherException {
    String url = getViewUrl(build);
    String commitMessage = null;
    Long commitDate = null;
    if (revision instanceof BuildRevisionEx) {
      Long modId = ((BuildRevisionEx) revision).getModificationId();
      if (modId != null) {
        SVcsModification m = myVcsHistory.findChangeById(modId);
        if (m != null) {
          commitMessage = m.getDescription();
          commitDate = m.getVcsDate().getTime();
        }
      }
    }
    String buildName = build.getFullName() + " #" + build.getBuildNumber();
    String payload = createPayload(myParams.get(Constants.UPSOURCE_PROJECT_ID),
            build.getBuildTypeExternalId(),
            status,
            buildName,
            url,
            description,
            getRevision(revision),
            commitMessage,
            commitDate);
    try {
      publish(payload, LogUtil.describe(build));
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to Upsource for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }


  private void publish(@NotNull String payload, @NotNull String buildDescription) {
    String url = HttpHelper.stripTrailingSlash(myParams.get(Constants.UPSOURCE_SERVER_URL)) + "/" + UpsourceSettings.ENDPOINT_BUILD_STATUS;
    postJson(url, myParams.get(Constants.UPSOURCE_USERNAME),
             myParams.get(Constants.UPSOURCE_PASSWORD), payload, null, buildDescription);
  }

  @NotNull
  private String createPayload(@NotNull String project,
                               @NotNull String statusKey,
                               @NotNull UpsourceStatus status,
                               @NotNull String buildName,
                               @NotNull String buildUrl,
                               @NotNull String description,
                               @NotNull String commitRevision,
                               @Nullable String commitMessage,
                               @Nullable Long commitDate) {
    Map<String, String> data = new HashMap<String, String>();
    data.put(UpsourceSettings.PROJECT_FIELD, project);
    data.put(UpsourceSettings.KEY_FIELD, statusKey);
    data.put(UpsourceSettings.STATE_FIELD, status.getName());
    data.put(UpsourceSettings.BUILD_NAME_FIELD, buildName);
    data.put(UpsourceSettings.BUILD_URL_FIELD, buildUrl);
    data.put(UpsourceSettings.DESCRIPTION_FIELD, description);
    data.put(UpsourceSettings.REVISION_FIELD, commitRevision);
    if (commitMessage != null)
      data.put(UpsourceSettings.REVISION_MESSAGE_FIELD, commitMessage);
    if (commitDate != null)
      data.put(UpsourceSettings.REVISION_DATE_FIELD, commitDate.toString());
    return myGson.toJson(data);
  }

  private static String getRevision(BuildRevision revision) {
    VcsRootInstance vcs = revision.getRoot();
    String revisionNo = revision.getRevision();
    if (vcs.getVcsName().equals("svn")) {
      // TeamCity may add a branch name and/or date/time information to a revision number for SVN roots
      Matcher matcher = TEAMCITY_SVN_REVISION_PATTERN.matcher(revisionNo);
      if (matcher.matches()) {
        return matcher.group(2);
      }
    }
    return revisionNo;
  }
}
