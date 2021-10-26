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

package jetbrains.buildServer.commitPublisher.space;

import com.google.gson.Gson;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.vcs.VcsModification;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class SpacePublisher extends HttpBasedCommitStatusPublisher {

  private final WebLinks myLinks;
  private final SpaceConnectDescriber mySpaceConnector;
  private final Gson myGson = new Gson();

  SpacePublisher(@NotNull CommitStatusPublisherSettings settings,
                 @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                 @NotNull WebLinks links,
                 @NotNull Map<String, String> params,
                 @NotNull CommitStatusPublisherProblems problems,
                 @NotNull SpaceConnectDescriber spaceConnector) {
    super(settings, buildType, buildFeatureId, params, problems);
    myLinks = links;
    mySpaceConnector = spaceConnector;
  }

  @NotNull
  @Override
  public String toString() {
    return "space";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.SPACE_PUBLISHER_ID;
  }

  @Override
  public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, SpaceBuildStatus.RUNNING, DefaultStatusMessages.BUILD_STARTED);
    return true;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    SpaceBuildStatus status = build.getBuildStatus().isSuccessful() ? SpaceBuildStatus.SUCCEEDED : SpaceBuildStatus.FAILED;
    publish(build, revision, status, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, SpaceBuildStatus.TERMINATED, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildFailureDetected(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    publish(build, revision, SpaceBuildStatus.FAILING, build.getStatusDescriptor().getText());
    return true;
  }

  @Override
  public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) throws PublisherException {
    publish(build, revision, buildInProgress ? SpaceBuildStatus.RUNNING : SpaceBuildStatus.SUCCEEDED, "Build marked as successful");
    return true;
  }

  private void publish(@NotNull SBuild build,
                       @NotNull BuildRevision revision,
                       @NotNull SpaceBuildStatus status,
                       @NotNull String description) throws PublisherException {
    Date finishDate = build.getFinishDate();
    List<String> changes = build.getContainingChanges()
      .stream()
      .limit(200)
      .map(VcsModification::getVersion)
      .collect(Collectors.toList());

    String payload = createPayload(
      changes,
      status,
      myLinks.getViewResultsUrl(build),
      SpaceSettings.getDisplayName(myParams),
      build.getFullName(),
      build.getBuildTypeExternalId(),
      (finishDate == null ? build.getServerStartDate() : finishDate).getTime(),
      description
    );

    SpaceToken token;
    String buildDescription = LogUtil.describe(build);
    try {
      token = SpaceToken.requestToken(
        mySpaceConnector.getServiceId(),
        mySpaceConnector.getServiceSecret(),
        mySpaceConnector.getFullAddress(),
        getConnectionTimeout(),
        myGson,
        getSettings().trustStore()
      );
    } catch (Exception e) {
      myProblems.reportProblem("Commit Status Publisher has failed to obtain a token from JetBrains Space for VCS root " + revision.getRoot().getName(),
                               this, buildDescription, null, e, LOG);
      return;
    }

    Repository repoInfo= SpaceUtils.getRepositoryInfo(revision.getRoot(), myParams.get(Constants.SPACE_PROJECT_KEY));

    String url = SpaceApiUrls.commitStatusUrl(
      mySpaceConnector.getFullAddress(),
      repoInfo.owner(),
      repoInfo.repositoryName(),
      revision.getRevision()
    );

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(HttpHeaders.ACCEPT, ContentType.TEXT_PLAIN.getMimeType());
    token.toHeader(headers);

    postJson(url, null, null, payload, headers, buildDescription);
  }

  @NotNull
  private String createPayload(@NotNull List<String> changes,
                               @NotNull SpaceBuildStatus executionStatus,
                               @NotNull String url,
                               @NotNull String externalServiceName,
                               @NotNull String taskName,
                               @NotNull String taskId,
                               Long timestamp,
                               String description) {
    Map<String, Object> data = new HashMap<>();
    data.put(SpaceSettings.CHANGES_FIELD, changes);
    data.put(SpaceSettings.EXECUTION_STATUS_FIELD, executionStatus.getName());
    data.put(SpaceSettings.BUILD_URL_FIELD, url);
    data.put(SpaceSettings.EXTERNAL_SERVICE_NAME_FIELD, externalServiceName);
    data.put(SpaceSettings.TASK_NAME_FIELD, taskName);
    data.put(SpaceSettings.TASK_ID_FIELD, taskId);

    if (timestamp != null)
      data.put(SpaceSettings.TIMESTAMP_FIELD, timestamp);
    if (description != null)
      data.put(SpaceSettings.DESCRIPTION_FIELD, description);

    return myGson.toJson(data);
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    int statusCode = response.getStatusCode();
    String responseContent = response.getContent();

    if (statusCode >= 400) {
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error: " + (responseContent != null ? responseContent : "<empty>"));
    }
  }
}
