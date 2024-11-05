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

package jetbrains.buildServer.commitPublisher.space;

import com.google.common.collect.ImmutableMap;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherTestBase;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.serverSide.oauth.space.*;
import jetbrains.buildServer.serverSide.oauth.space.application.ApplicationInformationCapabilityResolver;
import jetbrains.buildServer.serverSide.oauth.space.application.SpaceApplicationInformation;
import jetbrains.buildServer.serverSide.oauth.space.application.SpaceApplicationInformationManager;
import jetbrains.buildServer.serverSide.oauth.space.pojo.SpaceApplicationRights;
import jetbrains.buildServer.serverSide.oauth.space.pojo.SpaceRight;
import jetbrains.buildServer.serverSide.oauth.space.pojo.SpaceRightStatus;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SpaceSettingsTest extends CommitStatusPublisherTestBase {

  private static final String TEST_CLIENT_ID = "space-client-id";
  private static final String TEST_CLIENT_SECRET = "space-client-secret";
  private static final String TEST_SERVER_URL = "https://space.test.local";
  private static final String DUMMY_OAUTH_PROVIDER_TYPE = "DummyOAuthProvider";

  private SpaceApplicationInformationManager myMockApplicationInformationManager;
  private ApplicationInformationCapabilityResolver myMockCapabilityResolver;
  private OAuthConnectionsManager myConnectionsManager;
  private OAuthTokensStorage myTokenStorage;
  private SpaceSettings mySettings;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myMockApplicationInformationManager = Mockito.mock(SpaceApplicationInformationManager.class);
    when(myMockApplicationInformationManager.getForConnection(any(SpaceConnectDescriber.class))).thenReturn(SpaceApplicationInformation.none());
    myMockCapabilityResolver = Mockito.mock(ApplicationInformationCapabilityResolver.class);
    final SpaceClientFactory mockFactory = Mockito.mock(SpaceClientFactory.class);
    final SpaceOAuthProvider spaceOAuthProvider = new SpaceOAuthProvider(myWebLinks, mockFactory, myMockCapabilityResolver);
    myServer.registerExtension(OAuthProvider.class, "test", spaceOAuthProvider);
    myServer.registerExtension(OAuthProvider.class, "test2", new DummyOAuthProvider());

    final SSLTrustStoreProvider trustStoreProvider = () -> null;
    final SecurityContextImpl securityContext = myFixture.getSecurityContext();
    myTokenStorage = myFixture.getSingletonService(OAuthTokensStorage.class);
    myConnectionsManager = myFixture.getSingletonService(OAuthConnectionsManager.class);
    mySettings = new SpaceSettings(new MockPluginDescriptor(),
                                   myWebLinks,
                                   myProblems,
                                   trustStoreProvider,
                                   myConnectionsManager,
                                   securityContext,
                                   myMockApplicationInformationManager);
  }

  @Test
  public void createFeatureLessPublisher_notSupported_byDefault() {
    final SVcsRoot vcsRoot = addVcsRoot(myBuildType);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(myBuildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  public void createFeatureLessPublisher_enabledAndSuitableBuildType() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addSpaceConnection(project);
    mockPublishBuildStatusCapability(connection);
    final OAuthToken token = createAccessToken(connection);
    final SVcsRoot vcsRoot = addVcsRoot(buildType, connection, token);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNotNull();
    then(publisher.getId()).isEqualTo(Constants.SPACE_PUBLISHER_ID);
    then(publisher.getBuildFeatureId()).isNotNull();
    then(publisher.getBuildType()).isEqualTo(buildType);
    then(publisher.getVcsRootId()).isEqualTo(String.valueOf(vcsRoot.getId()));
  }

  @Test
  public void createFeatureLessPublisher_notSupported_noRefreshableToken() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addSpaceConnection(project);
    mockPublishBuildStatusCapability(connection);
    final SVcsRoot vcsRoot = addVcsRoot(buildType);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  public void createFeatureLessPublisher_notSupported_capabilityMissing() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addSpaceConnection(project);
    final OAuthToken token = createAccessToken(connection);
    final SVcsRoot vcsRoot = addVcsRoot(buildType, connection, token);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  public void createFeatureLessPublisher_notSupported_nonSpaceConnection() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addNonSpaceConnection(project);
    final OAuthToken token = createAccessToken(connection);
    final SVcsRoot vcsRoot = addVcsRoot(buildType, connection, token);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  public void createFeatureLessPublisher_notSupported_connectionMissing() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final SVcsRoot vcsRoot = addVcsRoot(buildType);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @DataProvider
  static Object[][] fetchUrls() {
    return new Object[][]{
      {"ssh://git@git.jetbrains.space/test-org/test-project/testrepo.git", "https://test-org.jetbrains.space"},
      {"https://git.jetbrains.space/test-org/test-project/testrepo.git", "https://test-org.jetbrains.space"},
      {"ssh://git@git.mycompany.test/test-project/testrepo.git", "https://mycompany.test"},
      {"ssh://git@git.mycompany.test/tEsT-pRoJeCt/testrepo.git", "https://mycompany.test"},
      {"ssh://git@git.mycompany.test/test-project/testrepo.git", "https://www.mycompany.test"},
      {"https://git.mycompany.test/test-project/testrepo.git", "https://mycompany.test"},
      {"user@git.mycompany.test:/test-project/testrepo", "https://mycompany.test"},
      {"ssh://git@spaceserver/test-project/testrepo.git", "http://spaceserver"},
    };
  }

  @Test(dataProvider = "fetchUrls")
  public void createFeatureLessPublisher_guessFromFetchUrl(@NotNull String fetchUrl, @NotNull String serviceUrl) {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    addSpaceConnection(project, serviceUrl);
    mockApplicationInfoWithPublishingRights(SpaceConstants.CONTEXT_IDENTIFIER_PROJECT_KEY + "TEST-PROJECT");
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final SVcsRoot vcsRoot = addVcsRoot(buildType, fetchUrl);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNotNull();
    then(publisher.getId()).isEqualTo(Constants.SPACE_PUBLISHER_ID);
    then(publisher.getBuildFeatureId()).isNotNull();
    then(publisher.getBuildType()).isEqualTo(buildType);
    then(publisher.getVcsRootId()).isEqualTo(String.valueOf(vcsRoot.getId()));
  }

  @Test
  public void createFeatureLessPublisher_guessFromFetchUrl_globalRights() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    addSpaceConnection(project, "https://test-org.jetbrains.space");
    mockApplicationInfoWithPublishingRights(SpaceConstants.CONTEXT_IDENTIFIER_GLOBAL);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final SVcsRoot vcsRoot = addVcsRoot(buildType, "ssh://git@git.jetbrains.space/test-org/test-project/testrepo.git");

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNotNull();
    then(publisher.getId()).isEqualTo(Constants.SPACE_PUBLISHER_ID);
    then(publisher.getBuildFeatureId()).isNotNull();
    then(publisher.getBuildType()).isEqualTo(buildType);
    then(publisher.getVcsRootId()).isEqualTo(String.valueOf(vcsRoot.getId()));
  }

  @DataProvider
  static Object[][] notMatchingUrls() {
    return new Object[][]{
      {"ssh://git@git.jetbrains.space/other-org/test-project/testrepo.git", "https://test-org.jetbrains.space"},
      {"ssh://git@git.mycompany.test/other-project/testrepo.git", "https://mycompany.test"},
      {"ssh://git@git.mycompany.test/testrepo.git", "https://mycompany.test"},
      {"https://invalid.test", "https://mycompany.test"},
      {"    ", "https://mycompany.test"},
    };
  }

  @Test(dataProvider = "notMatchingUrls")
  public void createFeatureLessPublisher_guessFromFetchUrl_notMatching(@NotNull String notMatchingUrl, @NotNull String serviceUrl) {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    addSpaceConnection(project, serviceUrl);
    mockApplicationInfoWithPublishingRights(SpaceConstants.CONTEXT_IDENTIFIER_PROJECT_KEY + "TEST-PROJECT");
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final SVcsRoot vcsRoot = addVcsRoot(buildType, notMatchingUrl);

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  public void createFeatureLessPublisher_guessFromFetchUrl_wrongRights() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);
    addSpaceConnection(project, "https://test-org.jetbrains.space");
    mockApplicationInfoWithPublishingRights(SpaceConstants.CONTEXT_IDENTIFIER_PROJECT_KEY + "OTHER-PROJECT");
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final SVcsRoot vcsRoot = addVcsRoot(buildType, "ssh://git@git.jetbrains.space/test-org/test-project/testrepo.git");

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  public void createFeatureLessPublisher_guessFromFetchUrl_noConnection() {
    final ProjectEx project = createProject("testproject");
    enableUnconditionalStatusPublishing(project);

    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final SVcsRoot vcsRoot = addVcsRoot(buildType, "ssh://git@git.jetbrains.team/test-project/testrepo.git");

    final CommitStatusPublisher publisher = mySettings.createFeaturelessPublisher(buildType, vcsRoot);

    then(publisher).isNull();
  }

  @Test
  @TestFor(issues = "TW-87183")
  public void isFeatureLessPublishingSupported_must_be_false_by_default() {
    final ProjectEx project = createProject("project");
    final BuildTypeEx buildType = project.createBuildType("testbuild");

    final boolean supported = mySettings.isFeatureLessPublishingSupported(buildType);

    then(supported).as("featureless publishing supported").isFalse();
  }

  @Test
  @TestFor(issues = "TW-87183")
  public void isFeatureLessPublishingSupported_must_be_false_when_no_vcs_root_matches() {
    final ProjectEx project = createProject("project");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addSpaceConnection(project);
    mockPublishBuildStatusCapability(connection);

    final boolean supported = mySettings.isFeatureLessPublishingSupported(buildType);

    then(supported).as("featureless publishing supported").isFalse();
  }

  @Test
  @TestFor(issues = "TW-87183")
  public void isFeatureLessPublishingSupported_should_be_true_with_vcs_root_and_connection() {
    final ProjectEx project = createProject("project");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addSpaceConnection(project);
    mockPublishBuildStatusCapability(connection);
    final OAuthToken token = createAccessToken(connection);
    addVcsRoot(buildType, connection, token);

    final boolean supported = mySettings.isFeatureLessPublishingSupported(buildType);

    then(supported).as("featureless publishing supported").isTrue();
  }


  @Test
  @TestFor(issues = "TW-87290")
  public void isFeatureLessPublishingSupported_should_be_true_with_vcs_root_without_token_id_and_connection() {
    final ProjectEx project = createProject("project");
    enableUnconditionalStatusPublishing(project);
    final BuildTypeEx buildType = project.createBuildType("testbuild");
    final OAuthConnectionDescriptor connection = addSpaceConnection(project, "http://space.test");
    mockPublishBuildStatusCapability(connection);
    addVcsRoot(buildType,"http://space.test/myorg/myproject/myrepo.git");

    final boolean supported = mySettings.isFeatureLessPublishingSupported(buildType);

    then(supported).as("featureless publishing supported").isTrue();
  }

  private void enableUnconditionalStatusPublishing(@NotNull ProjectEx project) {
    project.addParameter(getParameterFactory().createSimpleParameter(SpaceConstants.FEATURE_TOGGLE_UNCONDITIONAL_COMMIT_STATUS, "true"));
  }

  @NotNull
  private OAuthConnectionDescriptor addSpaceConnection(@NotNull ProjectEx project) {
    return addSpaceConnection(project, null);
  }

  @NotNull
  private OAuthConnectionDescriptor addSpaceConnection(@NotNull ProjectEx project, @Nullable String serviceUrl) {
    final String url = serviceUrl != null ? serviceUrl : TEST_SERVER_URL;
    return myConnectionsManager.addConnection(project, SpaceOAuthProvider.TYPE, ImmutableMap.of(
      SpaceOAuthKeys.SPACE_CLIENT_ID, TEST_CLIENT_ID,
      SpaceOAuthKeys.SPACE_CLIENT_SECRET, TEST_CLIENT_SECRET,
      SpaceOAuthKeys.SPACE_SERVER_URL, url
    ));
  }

  @NotNull
  private OAuthConnectionDescriptor addNonSpaceConnection(@NotNull ProjectEx project) {
    return myConnectionsManager.addConnection(project, DUMMY_OAUTH_PROVIDER_TYPE, Collections.emptyMap());
  }

  private void mockPublishBuildStatusCapability(@NotNull OAuthConnectionDescriptor connection) {
    when(myMockCapabilityResolver.apply(any(OAuthConnectionDescriptor.class), Mockito.eq(ConnectionCapability.PUBLISH_BUILD_STATUS)))
      .thenAnswer(invocation -> {
        final OAuthConnectionDescriptor connectionArgument = invocation.getArgument(0);
        return connectionArgument.getId().equals(connection.getId());
      });
  }

  private void mockApplicationInfoWithPublishingRights(@NotNull String contextIdentifier) {
    final Map<SpaceRight, SpaceRightStatus> rights =
      SpaceConstants.RIGHTS_COMMIT_STATUS.stream()
                                         .collect(Collectors.toMap(Function.identity(),
                                                                   right -> SpaceRightStatus.GRANTED,
                                                                   (a, b) -> b,
                                                                   () -> new EnumMap<>(SpaceRight.class)));
    final SpaceApplicationRights commitStatusRights = new SpaceApplicationRights(contextIdentifier, rights);
    final Map<String, SpaceApplicationRights> projectRights = ImmutableMap.of(contextIdentifier, commitStatusRights);

    SpaceApplicationInformation info = new SpaceApplicationInformation(false, false, projectRights, "test-app-id", null);
    when(myMockApplicationInformationManager.getForConnection(any(SpaceConnectDescriber.class))).thenReturn(info);
  }

  @NotNull
  private OAuthToken createAccessToken(@NotNull OAuthConnectionDescriptor connection) {
    final OAuthToken token = new OAuthToken("access-token", "**", "x-oauth-user", 600, -1, new Date().getTime());
    return myTokenStorage.rememberToken(connection.getTokenStorageId(), token);
  }

  @NotNull
  private SVcsRoot addVcsRoot(@NotNull BuildTypeEx buildType) {
    return addVcsRoot(buildType, null, null, null);
  }

  @NotNull
  private SVcsRoot addVcsRoot(@NotNull BuildTypeEx buildType, @NotNull String fetchUrl) {
    return addVcsRoot(buildType, null, null, fetchUrl);
  }

  @NotNull
  private SVcsRoot addVcsRoot(@NotNull BuildTypeEx buildType, @NotNull OAuthConnectionDescriptor connection, @NotNull OAuthToken token) {
    return addVcsRoot(buildType, connection, token, null);
  }

  @NotNull
  private SVcsRoot addVcsRoot(@NotNull BuildTypeEx buildType, @Nullable OAuthConnectionDescriptor connection, @Nullable OAuthToken token, @Nullable String fetchUrl) {
    final SVcsRootImpl vcsRoot = myFixture.addVcsRoot("jetbrains.git", "", buildType);
    final Map<String, String> properties = new HashMap<>();
    if (token != null && connection != null) {
      properties.put("tokenId", connection.buildFullTokenId(token.getId()));
    }
    if (fetchUrl != null) {
      properties.put("url", fetchUrl);
    }
    if (!properties.isEmpty()) {
      vcsRoot.setProperties(properties);
    }
    return vcsRoot;
  }

  static class DummyOAuthProvider extends OAuthProvider {
    @NotNull
    @Override
    public String getType() {
      return DUMMY_OAUTH_PROVIDER_TYPE;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "dummy provider for test";
    }
  }
}
