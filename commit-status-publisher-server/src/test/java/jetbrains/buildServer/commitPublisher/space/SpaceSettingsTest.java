package jetbrains.buildServer.commitPublisher.space;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Date;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherTestBase;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.serverSide.oauth.*;
import jetbrains.buildServer.serverSide.oauth.space.SpaceClientFactory;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConstants;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthKeys;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.space.application.ApplicationInformationCapabilityResolver;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SpaceSettingsTest extends CommitStatusPublisherTestBase {

  private static final String TEST_CLIENT_ID = "space-client-id";
  private static final String TEST_CLIENT_SECRET = "space-client-secret";
  private static final String TEST_SERVER_URL = "https://space.test.local";
  private static final String DUMMY_OAUTH_PROVIDER_TYPE = "DummyOAuthProvider";

  private ApplicationInformationCapabilityResolver myMockCapabilityResolver;
  private OAuthConnectionsManager myConnectionsManager;
  private OAuthTokensStorage myTokenStorage;
  private SpaceSettings mySettings;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myMockCapabilityResolver = Mockito.mock(ApplicationInformationCapabilityResolver.class);
    final SpaceClientFactory mockFactory = Mockito.mock(SpaceClientFactory.class);
    final SpaceOAuthProvider spaceOAuthProvider = new SpaceOAuthProvider(myWebLinks, mockFactory, myMockCapabilityResolver);
    myServer.registerExtension(OAuthProvider.class, "test", spaceOAuthProvider);
    myServer.registerExtension(OAuthProvider.class, "test2", new DummyOAuthProvider());

    final SSLTrustStoreProvider trustStoreProvider = () -> null;
    final SecurityContextImpl securityContext = myFixture.getSecurityContext();
    myTokenStorage = myFixture.getSingletonService(OAuthTokensStorage.class);
    myConnectionsManager = myFixture.getSingletonService(OAuthConnectionsManager.class);
    mySettings = new SpaceSettings(new MockPluginDescriptor(), myWebLinks, myProblems, trustStoreProvider, myConnectionsManager, myTokenStorage, securityContext);
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

  private void enableUnconditionalStatusPublishing(@NotNull ProjectEx project) {
    project.addParameter(getParameterFactory().createSimpleParameter(SpaceConstants.FEATURE_TOGGLE_UNCONDITIONAL_COMMIT_STATUS, "true"));
  }

  @NotNull
  private OAuthConnectionDescriptor addSpaceConnection(@NotNull ProjectEx project) {
    return myConnectionsManager.addConnection(project, SpaceOAuthProvider.TYPE, ImmutableMap.of(
      SpaceOAuthKeys.SPACE_CLIENT_ID, TEST_CLIENT_ID,
      SpaceOAuthKeys.SPACE_CLIENT_SECRET, TEST_CLIENT_SECRET,
      SpaceOAuthKeys.SPACE_SERVER_URL, TEST_SERVER_URL
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

  @NotNull
  private OAuthToken createAccessToken(@NotNull OAuthConnectionDescriptor connection) {
    final OAuthToken token = new OAuthToken("access-token", "**", "x-oauth-user", 600, -1, new Date().getTime());
    return myTokenStorage.rememberToken(connection.getTokenStorageId(), token);
  }

  private SVcsRoot addVcsRoot(@NotNull BuildTypeEx buildType) {
    return addVcsRoot(buildType, null, null);
  }

  private SVcsRoot addVcsRoot(@NotNull BuildTypeEx buildType, @Nullable OAuthConnectionDescriptor connection, @Nullable OAuthToken token) {
    final SVcsRootImpl vcsRoot = myFixture.addVcsRoot("jetbrains.git", "", buildType);
    if (token != null && connection != null) {
      vcsRoot.setProperties(ImmutableMap.of(
        "tokenId", connection.buildFullTokenId(token.getId())
      ));
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
