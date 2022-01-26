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

import java.util.List;
import java.util.Map;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author anton.zamolotskikh, 13/02/17.
 */
public class MockPublisherSettings extends DummyPublisherSettings {

  static final String PUBLISHER_ID = "MockPublisherId";
  private final CommitStatusPublisherProblems myProblems;
  private CommitStatusPublisher myPublisher = null;
  private List<String> myRootNamesToFailTestConnection = null;

  public MockPublisherSettings(CommitStatusPublisherProblems problems) {
    myProblems = problems;
  }

  @Override
  @NotNull
  public String getId() {
    return PUBLISHER_ID;
  }

  public void setPublisher(CommitStatusPublisher publisher) {
    myPublisher = publisher;
  }

  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return null == myPublisher ? new MockPublisher(this, getId(), buildType, buildFeatureId, params, myProblems, new PublisherLogger()) : myPublisher;
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return vcsRoot.getVcsName().equals("jetbrains.git");
  }

  @Override
  public boolean isEventSupported(final CommitStatusPublisher.Event event, final SBuildType buildType, final Map<String, String> params) {
    return true; // Mock publisher "supports" all events
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    if(null != myRootNamesToFailTestConnection && myRootNamesToFailTestConnection.contains(root.getName())) {
      throw new PublisherException(String.format("Test connection has failed for vcs root %s", root.getName()));
    }
  }

  public void setVcsRootsToFailTestConnection(List<String> rootNames)  {
    myRootNamesToFailTestConnection = rootNames;
  }
}
