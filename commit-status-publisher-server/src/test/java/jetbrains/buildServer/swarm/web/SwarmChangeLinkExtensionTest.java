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

package jetbrains.buildServer.swarm.web;

import java.util.Date;
import java.util.HashMap;
import jetbrains.buildServer.BaseWebTestCase;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.serverSide.impl.MockVcsModification;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.swarm.SwarmTestUtil;
import jetbrains.buildServer.util.cache.ResetCacheRegisterImpl;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class SwarmChangeLinkExtensionTest extends BaseWebTestCase {

  public static final String SWARM_ROOT = "http://swarm-root/";
  private SwarmChangeLinkExtension myExtension;
  private VcsRootInstance myVri;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    SwarmTestUtil.addSwarmFeature(myBuildType, SWARM_ROOT);

    SVcsRootImpl perforce = myFixture.addVcsRoot("perforce", "");
    myVri = myBuildType.getVcsRootInstanceForParent(perforce);
    SwarmClientManager swarmClientManager = new SwarmClientManager(myWebLinks, () -> null, new ResetCacheRegisterImpl());

    myExtension = new SwarmChangeLinkExtension(myWebManager, swarmClientManager, myProjectManager);
  }

  @Test
  public void should_be_available_with_swarm_feature() throws Exception {
    MockRequest request = prepareRequest();
    then(myExtension.isAvailable(request)).isTrue();
  }

  @Test
  public void should_not_be_available_without_swarm_feature() throws Exception {
    myBuildType.removeBuildFeature(myBuildType.getBuildFeatures().iterator().next().getId());

    MockRequest request = prepareRequest();
    then(myExtension.isAvailable(request)).isFalse();
  }

  @Test
  public void should_not_be_available_not_perforce_change_feature() throws Exception {
    SVcsRootImpl svnRoot = myFixture.addVcsRoot("svn", "");
    MockRequest request = new MockRequest();
    MockVcsModification modification = new MockVcsModification("kir", "desc", new Date(), "2233");
    modification.setRoot(myBuildType.getVcsRootInstanceForParent(svnRoot));

    request.setAttribute("modification", modification);
    request.setAttribute("buildType", myBuildType);
    then(myExtension.isAvailable(request)).isFalse();
  }

  @Test
  public void should_provide_swarm_link_ordinary_change() throws Exception {
    MockRequest request = prepareRequest();

    HashMap<String, Object> model = new HashMap<>();
    myExtension.fillModel(model, request);

    then(model)
      .containsEntry("swarmChangeUrl", SWARM_ROOT + "changes/1234")
      .containsEntry("showType", "compact");
  }

  @Test
  public void should_provide_swarm_link_for_personal_change() throws Exception {
    MockRequest request = new MockRequest();
    MockVcsModification modification = new MockVcsModification("kir", "Some personal change (shelved changelist @=43)", new Date(), "13 09 2022 16:51");
    modification.setPesonal(true);

    request.setAttribute("modification", modification);
    request.setAttribute("buildType", myBuildType);

    then(myExtension.isAvailable(request)).isTrue();

    HashMap<String, Object> model = new HashMap<>();
    myExtension.fillModel(model, request);

    then(model)
      .containsEntry("swarmChangeUrl", SWARM_ROOT + "changes/43")
      .containsEntry("showType", "compact");
  }

  @NotNull
  private MockRequest prepareRequest() {
    MockRequest request = new MockRequest();
    MockVcsModification modification = new MockVcsModification("kir", "desc", new Date(), "1234");
    modification.setRoot(myVri);

    request.setAttribute("modification", modification);
    request.setAttribute("buildType", myBuildType);
    return request;
  }

}
