

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

package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

@Test
public class ServerListenerTest extends BaseServerTestCase {

  private SVcsRoot myVcsRoot;

  private static final String MY_VCS_ID = "MY_VCS_ID";
  private static final String ANOTHER_VCS_ID = "ANOTHER_VCS_ID";

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myVcsRoot = myFixture.addVcsRoot("vcs", "vcs1");
    myVcsRoot.setExternalId(MY_VCS_ID);
    myBuildType.addVcsRoot(myVcsRoot);
    final EventDispatcher<ConfigActionsServerListener> dispatcher = myFixture.getSingletonService(ConfigActionsEventDispatcher.class);
    ServerListener listener = new ServerListener(dispatcher);
    myFixture.addService(listener);
  }

  public void must_change_vcs_root_external_id_if_renamed() {
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, Collections.singletonMap(Constants.VCS_ROOT_ID_PARAM, MY_VCS_ID));
    myVcsRoot.setExternalId(ANOTHER_VCS_ID);

    Collection<SBuildFeatureDescriptor> buildFeatures = myBuildType.getBuildFeatures();
    assertEquals(1, buildFeatures.size());

    Map<String, String> params = buildFeatures.iterator().next().getParameters();
    assertEquals(1, params.size());
    assertEquals(ANOTHER_VCS_ID, params.get(Constants.VCS_ROOT_ID_PARAM));
  }

  public void must_change_vcs_root_external_id_if_copied() {

    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, Collections.singletonMap(Constants.VCS_ROOT_ID_PARAM, MY_VCS_ID));

    SProject p2 = myProjectManager.copyProject(myProject, myProject.getParentProject(), new CopyOptions());

    final SVcsRoot vcsRootCopy = p2.getVcsRoots().iterator().next();
    final SBuildType btCopy = p2.getBuildTypes().iterator().next();

    Collection<SBuildFeatureDescriptor> buildFeatures = btCopy.getBuildFeatures();
    assertEquals(1, buildFeatures.size());

    Map<String, String> params = buildFeatures.iterator().next().getParameters();
    assertEquals(1, params.size());
    assertEquals(vcsRootCopy.getExternalId(), params.get(Constants.VCS_ROOT_ID_PARAM));
  }

  public void must_change_vcs_root_internal_id_to_external_if_copied() {
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, Collections.singletonMap(Constants.VCS_ROOT_ID_PARAM, String.valueOf(myVcsRoot.getId())));

    SProject p2 = myProjectManager.copyProject(myProject, myProject.getParentProject(), new CopyOptions());

    final SVcsRoot vcsRootCopy = p2.getVcsRoots().iterator().next();
    final SBuildType btCopy = p2.getBuildTypes().iterator().next();

    Collection<SBuildFeatureDescriptor> buildFeatures = btCopy.getBuildFeatures();
    assertEquals(1, buildFeatures.size());

    Map<String, String> params = buildFeatures.iterator().next().getParameters();
    assertEquals(1, params.size());
    assertEquals(vcsRootCopy.getExternalId(), params.get(Constants.VCS_ROOT_ID_PARAM));
  }
}