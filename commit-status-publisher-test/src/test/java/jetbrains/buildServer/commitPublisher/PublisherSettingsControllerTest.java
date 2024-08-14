

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

import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.controllers.MockResponse;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.web.util.SessionUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class PublisherSettingsControllerTest extends CommitStatusPublisherTestBase {

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  private SUser setUserWithRole(final Role projectViewerRole) {
    SUser user = myFixture.createUserAccount("me");
    user.addRole(RoleScope.projectScope(myProject.getProjectId()), projectViewerRole);
    myFixture.getSecurityContext().setAuthorityHolder(user);
    return user;
  }

  public void must_test_for_all_relevant_roots() throws Exception {
    HttpServletRequest request = new MockRequest(
      Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID,
      Constants.TEST_CONNECTION_PARAM, Constants.TEST_CONNECTION_YES,
      "projectId", myProject.getExternalId(),
      "id", "buildType:" + myBuildType.getExternalId()
    );
    HttpServletResponse response = new MockResponse();

    SUser user = setUserWithRole(getProjectAdminRole());
    SessionUser.setUser(request, user);

    SVcsRoot vcs1 = myFixture.addVcsRoot("jetbrains.git", "one");
    vcs1.setName("VCS Root 1");
    SVcsRoot vcs2 = myFixture.addVcsRoot("jetbrains.git", "two");
    vcs2.setName("VCS Root 2");
    SVcsRoot vcs3 = myFixture.addVcsRoot("jetbrains.git", "three");
    vcs3.setName("VCS Root 3");
    SVcsRoot vcs4 = myFixture.addVcsRoot("svn", "four"); // MockPublisherSettings ignore svn roots
    vcs4.setName("VCS Root 4");
    myBuildType.addVcsRoot(vcs1);
    myBuildType.addVcsRoot(vcs2);
    myBuildType.addVcsRoot(vcs3);
    myBuildType.addVcsRoot(vcs4);

    myPublisherSettings.setVcsRootsToFailTestConnection(Arrays.asList("VCS Root 1", "VCS Root 3", "VCS Root 4"));

    mySettingsController.handleRequestInternal(request, response);

    then(((MockResponse)response).getReturnedContent())
      .contains("VCS Root 1")
      .doesNotContain("VCS Root 2")
      .contains("VCS Root 3")
      .doesNotContain("VCS Root 4");
  }

  public void must_not_report_no_relevant_vcs_if_failure() throws Exception {
    HttpServletRequest request = new MockRequest(
      Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID,
      Constants.TEST_CONNECTION_PARAM, Constants.TEST_CONNECTION_YES,
      "projectId", myProject.getExternalId(),
      "id", "buildType:" + myBuildType.getExternalId()
    );
    HttpServletResponse response = new MockResponse();

    SUser user = setUserWithRole(getProjectAdminRole());
    SessionUser.setUser(request, user);

    SVcsRoot vcs1 = myFixture.addVcsRoot("jetbrains.git", "one");
    vcs1.setName("VCS Root 1");
    SVcsRoot vcs2 = myFixture.addVcsRoot("jetbrains.git", "two");
    vcs2.setName("VCS Root 2");
    myBuildType.addVcsRoot(vcs1);
    myBuildType.addVcsRoot(vcs2);

    myPublisherSettings.setVcsRootsToFailTestConnection(Arrays.asList("VCS Root 1", "VCS Root 2"));

    mySettingsController.handleRequestInternal(request, response);

    then(((MockResponse)response).getReturnedContent())
      .contains("VCS Root 1")
      .contains("VCS Root 2")
      .doesNotContain("No relevant VCS");
  }

  public void must_report_no_relevant_vcs() throws Exception {
    HttpServletRequest request = new MockRequest(
      Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID,
      Constants.TEST_CONNECTION_PARAM, Constants.TEST_CONNECTION_YES,
      "projectId", myProject.getExternalId(),
      "id", "buildType:" + myBuildType.getExternalId()
    );
    HttpServletResponse response = new MockResponse();

    SUser user = setUserWithRole(getProjectAdminRole());
    SessionUser.setUser(request, user);

    SVcsRoot vcs1 = myFixture.addVcsRoot("svn", "one");
    vcs1.setName("VCS Root 1");
    SVcsRoot vcs2 = myFixture.addVcsRoot("svn", "two");
    vcs2.setName("VCS Root 2");
    myBuildType.addVcsRoot(vcs1);
    myBuildType.addVcsRoot(vcs2);

    myPublisherSettings.setVcsRootsToFailTestConnection(Arrays.asList("VCS Root 1", "VCS Root 2"));

    mySettingsController.handleRequestInternal(request, response);

    then(((MockResponse)response).getReturnedContent())
      .doesNotContain("VCS Root 1")
      .doesNotContain("VCS Root 2")
      .contains("No relevant VCS");
  }

  public void user_has_no_permissions_test() throws Exception {
    HttpServletRequest request = new MockRequest(
      Constants.PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID,
      Constants.TEST_CONNECTION_PARAM, Constants.TEST_CONNECTION_YES,
      "projectId", myProject.getExternalId(),
      "id", "buildType:" + myBuildType.getExternalId()
    );
    HttpServletResponse response = new MockResponse();

    SUser user = setUserWithRole(getProjectViewerRole());
    SessionUser.setUser(request, user);

    SVcsRoot vcs1 = myFixture.addVcsRoot("svn", "one");
    vcs1.setName("VCS Root 1");
    SVcsRoot vcs2 = myFixture.addVcsRoot("svn", "two");
    vcs2.setName("VCS Root 2");
    myBuildType.addVcsRoot(vcs1);
    myBuildType.addVcsRoot(vcs2);

    myPublisherSettings.setVcsRootsToFailTestConnection(Arrays.asList("VCS Root 1", "VCS Root 2"));

    mySettingsController.handleRequestInternal(request, response);

    assertEquals(404, response.getStatus());
  }
}