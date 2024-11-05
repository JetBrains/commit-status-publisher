

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.gerrit;

import java.util.Map;
import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import org.jetbrains.annotations.NotNull;

class GerritPublisher extends BaseCommitStatusPublisher {

  private final WebLinks myLinks;
  private final GerritClient myGerritClient;

  GerritPublisher(@NotNull CommitStatusPublisherSettings settings,
                  @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                  @NotNull GerritClient gerritClient,
                  @NotNull WebLinks links,
                  @NotNull Map<String, String> params,
                  @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
    myLinks = links;
    myGerritClient = gerritClient;
  }

  @NotNull
  public String toString() {
    return "gerrit";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GERRIT_PUBLISHER_ID;
  }

  @Override
  public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
    Branch branch = build.getBranch();
    if (branch == null || branch.isDefaultBranch())
      return false;

    String vote = build.getBuildStatus().isSuccessful() ? getSuccessVote() : getFailureVote();
    String msg = build.getFullName() +
            " #" + build.getBuildNumber() +
            ": " + build.getStatusDescriptor().getText() +
            " " + getViewUrl(build);

    try {
      SBuildType bt = build.getBuildType();
      if (null == bt) return false;

      myGerritClient.review(
        new GerritConnectionDetails(bt.getProject(), getGerritProject(), getGerritServer(), getUsername(),
                                    myParams.get(ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP)),
        getGerritLabel(), vote, msg, revision.getRevision()
      );
      return true;
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to Gerrit for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  @Override
  protected WebLinks getLinks() {
    return myLinks;
  }

  private String getGerritServer() {
    return myParams.get(Constants.GERRIT_SERVER);
  }

  private String getGerritProject() {
    return myParams.get(Constants.GERRIT_PROJECT);
  }

  private String getGerritLabel() {
    return myParams.get(Constants.GERRIT_LABEL);
  }

  private String getUsername() {
    return myParams.get(Constants.GERRIT_USERNAME);
  }

  private String getSuccessVote() {
    return myParams.get(Constants.GERRIT_SUCCESS_VOTE);
  }

  private String getFailureVote() {
    return myParams.get(Constants.GERRIT_FAILURE_VOTE);
  }
}