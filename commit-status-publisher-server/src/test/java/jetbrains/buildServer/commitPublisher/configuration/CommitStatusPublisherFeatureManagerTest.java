package jetbrains.buildServer.commitPublisher.configuration;

import com.google.common.collect.ImmutableMap;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.BitbucketCloudBuildNameProvider;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.BitbucketCloudCommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.BitbucketCloudSettings;
import jetbrains.buildServer.commitPublisher.github.ChangeStatusUpdater;
import jetbrains.buildServer.commitPublisher.github.GitHubBuildContextProvider;
import jetbrains.buildServer.commitPublisher.github.GitHubCommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.commitPublisher.github.GitHubSettings;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapperImpl;
import jetbrains.buildServer.commitPublisher.gitlab.GitLabBuildNameProvider;
import jetbrains.buildServer.commitPublisher.gitlab.GitLabCommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.commitPublisher.gitlab.GitlabSettings;
import jetbrains.buildServer.commitPublisher.stash.BitbucketServerCommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.commitPublisher.stash.StashBuildNameProvider;
import jetbrains.buildServer.commitPublisher.stash.StashSettings;
import jetbrains.buildServer.commitPublisher.tfs.AzureDevOpsCommitStatusPublisherFeatureBuilderService;
import jetbrains.buildServer.commitPublisher.tfs.TfsBuildNameProvider;
import jetbrains.buildServer.commitPublisher.tfs.TfsPublisherSettings;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.oauth.azuredevops.AzureDevOpsOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.github.GHEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.github.GitHubOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabCEorEEOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.gitlab.GitLabComOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.tfs.TfsAuthProvider;
import jetbrains.buildServer.serverSide.systemProblems.BuildProblemsTicketManager;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsModificationHistoryEx;
import jetbrains.buildServer.vcshostings.VcsHostingTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class CommitStatusPublisherFeatureManagerTest extends BaseServerTestCase {
  private AutoCloseable myAutoCloseable;

  @Mock
  private VcsHostingTypeProvider myVcsHostingTypeProvider;

  private CommitStatusPublisherFeatureManager myManager;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myAutoCloseable = openMocks(this);
    myManager = new CommitStatusPublisherFeatureManager(myVcsHostingTypeProvider, new CommitStatusPublisherFeatureBuilderFactory(myServer));

    setupPublisherSettings();
    setupFeatureBuilderServices();
  }

  @Override
  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
    myAutoCloseable.close();
  }

  @Test
  public void configureBuildFeature_new_happyPath_GitHub() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);
    when(myVcsHostingTypeProvider.getHostingType(Mockito.eq(myBuildType), Mockito.eq(vcsRoot))).thenReturn(GitHubOAuthProvider.TYPE);

    SBuildFeatureDescriptor configuredBuildFeature = myManager.configureMinimalBuildFeature(myBuildType, vcsRoot);
    then(configuredBuildFeature).isNotNull();
    then(configuredBuildFeature.getParameters()).containsOnly(
      entry("publisherId", "githubStatusPublisher"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("github_host", "https://api.github.com"),
      entry("github_authentication_type", "vcsRoot")
    );
  }

  @Test
  public void configureBuildFeature_new_happyPath_Gitlab() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);
    when(myVcsHostingTypeProvider.getHostingType(Mockito.eq(myBuildType), Mockito.eq(vcsRoot))).thenReturn(GitLabComOAuthProvider.TYPE);

    SBuildFeatureDescriptor configuredBuildFeature = myManager.configureMinimalBuildFeature(myBuildType, vcsRoot);
    then(configuredBuildFeature).isNotNull();
    then(configuredBuildFeature.getParameters()).containsOnly(
      entry("publisherId", "gitlabStatusPublisher"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("authType", "vcsRoot")
    );
  }

  @Test
  public void configureBuildFeature_new_happyPath_BitBucketCloud() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);
    when(myVcsHostingTypeProvider.getHostingType(Mockito.eq(myBuildType), Mockito.eq(vcsRoot))).thenReturn(BitBucketOAuthProvider.TYPE);

    SBuildFeatureDescriptor configuredBuildFeature = myManager.configureMinimalBuildFeature(myBuildType, vcsRoot);
    then(configuredBuildFeature).isNotNull();
    then(configuredBuildFeature.getParameters()).containsOnly(
      entry("publisherId", "bitbucketCloudPublisher"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("authType", "vcsRoot")
    );
  }

  @Test
  public void configureBuildFeature_new_happyPath_BitBucketServer() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);
    when(myVcsHostingTypeProvider.getHostingType(Mockito.eq(myBuildType), Mockito.eq(vcsRoot))).thenReturn(Constants.STASH_OAUTH_PROVIDER_TYPE);

    SBuildFeatureDescriptor configuredBuildFeature = myManager.configureMinimalBuildFeature(myBuildType, vcsRoot);
    then(configuredBuildFeature).isNotNull();
    then(configuredBuildFeature.getParameters()).containsOnly(
      entry("publisherId", "atlassianStashPublisher"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("authType", "vcsRoot")
    );
  }

  @Test
  public void configureBuildFeature_new_happyPath_Tfs() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);
    when(myVcsHostingTypeProvider.getHostingType(Mockito.eq(myBuildType), Mockito.eq(vcsRoot))).thenReturn(TfsAuthProvider.TYPE);

    SBuildFeatureDescriptor configuredBuildFeature = myManager.configureMinimalBuildFeature(myBuildType, vcsRoot);
    then(configuredBuildFeature).isNotNull();
    then(configuredBuildFeature.getParameters()).containsOnly(
      entry("publisherId", "tfs"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("tfsAuthType", "vcsRoot"),
      entry("publish.pull.requests", "true")
    );
  }

  @Test
  public void configureBuildFeature_new_happyPath_AzureDevOps() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);
    when(myVcsHostingTypeProvider.getHostingType(Mockito.eq(myBuildType), Mockito.eq(vcsRoot))).thenReturn(AzureDevOpsOAuthProvider.TYPE);

    SBuildFeatureDescriptor configuredBuildFeature = myManager.configureMinimalBuildFeature(myBuildType, vcsRoot);
    then(configuredBuildFeature).isNotNull();
    then(configuredBuildFeature.getParameters()).containsOnly(
      entry("publisherId", "tfs"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("tfsAuthType", "vcsRoot"),
      entry("publish.pull.requests", "true")
    );
  }

  @DataProvider
  public static Object[][] isBuildFeatureSupportedArgs() {
    return new Object[][]{
      {GitHubOAuthProvider.TYPE, true},
      {GHEOAuthProvider.TYPE, true},
      {"GitHubApp", true},
      {BitBucketOAuthProvider.TYPE, true},
      {GitLabComOAuthProvider.TYPE, true},
      {GitLabCEorEEOAuthProvider.TYPE, true},
      {AzureDevOpsOAuthProvider.TYPE, true},
      {TfsAuthProvider.TYPE, true},
      {"BitbucketServer",  true},
      {"JetBrains Space",  false},
    };
  }

  @Test(dataProvider = "isBuildFeatureSupportedArgs")
  public void isBuildFeatureSupported(@NotNull String connectionType, boolean expectedResult) {
    final OAuthConnectionDescriptor connection = mockConnectionOfType(connectionType);

    then(myManager.isBuildFeatureSupported(connection)).isEqualTo(expectedResult);
  }

  @NotNull
  private OAuthConnectionDescriptor mockConnectionOfType(@NotNull String type) {
    final OAuthConnectionDescriptor mockConnection = Mockito.mock(OAuthConnectionDescriptor.class);
    when(mockConnection.getProviderType()).thenReturn(type);

    return mockConnection;
  }

  private void setupFeatureBuilderServices() {
    myServer.registerExtension(CommitStatusPublisherFeatureBuilderService.class, "BitbucketCloudCommitStatusPublisherFeatureBuilderService",
                               new BitbucketCloudCommitStatusPublisherFeatureBuilderService());
    myServer.registerExtension(CommitStatusPublisherFeatureBuilderService.class, "GitHubCommitStatusPublisherFeatureBuilderService",
                               new GitHubCommitStatusPublisherFeatureBuilderService());
    myServer.registerExtension(CommitStatusPublisherFeatureBuilderService.class, "GitLabCommitStatusPublisherFeatureBuilderService",
                               new GitLabCommitStatusPublisherFeatureBuilderService());
    myServer.registerExtension(CommitStatusPublisherFeatureBuilderService.class, "AzureDevOpsCommitStatusPublisherFeatureBuilderService",
                               new AzureDevOpsCommitStatusPublisherFeatureBuilderService());
    myServer.registerExtension(CommitStatusPublisherFeatureBuilderService.class, "BitbucketServerCommitStatusPublisherFeatureBuilderService",
                               new BitbucketServerCommitStatusPublisherFeatureBuilderService());
  }

  private void setupPublisherSettings() {
    @SuppressWarnings("deprecation") final ChangeStatusUpdater changeStatusUpdater =
      new ChangeStatusUpdater(new GitHubApiFactoryImpl(new HttpClientWrapperImpl(new HTTPRequestBuilder.ApacheClient43RequestHandler(), () -> null),
                                                       myFixture.getSingletonService(OAuthTokensStorage.class),
                                                       myFixture.getSingletonService(OAuthConnectionsManager.class),
                                                       myFixture.getProjectManager()), myFixture.getVcsHistory());

    final CommitStatusPublisherProblems problems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));
    final SSLTrustStoreProvider trustStoreProvider = () -> null;
    final VcsModificationHistoryEx history = myFixture.getVcsHistory();

    @SuppressWarnings("deprecation") final GitHubSettings gitHubSettings =
      new GitHubSettings(changeStatusUpdater, new MockPluginDescriptor(), myWebLinks, problems,
                         myFixture.getSingletonService(OAuthConnectionsManager.class),
                         myFixture.getSingletonService(OAuthTokensStorage.class),
                         myFixture.getSecurityContext(),
                         trustStoreProvider,
                         new GitHubBuildContextProvider()
      );

    @SuppressWarnings("deprecation") final CommitStatusPublisherSettings gitlabSettings =
      new GitlabSettings(new MockPluginDescriptor(), myWebLinks, problems, trustStoreProvider, history,
                         myFixture.getSingletonService(OAuthConnectionsManager.class),
                         myFixture.getSingletonService(OAuthTokensStorage.class),
                         getUserModelEx(),
                         myFixture.getSecurityContext(),
                         myFixture,
                         new GitLabBuildNameProvider()
      );

    @SuppressWarnings("deprecation") final CommitStatusPublisherSettings bitbucketCloudSettings = new BitbucketCloudSettings(
      new MockPluginDescriptor(),
      myWebLinks,
      problems,
      trustStoreProvider,
      myFixture.getSingletonService(OAuthConnectionsManager.class),
      myFixture.getSingletonService(OAuthTokensStorage.class),
      getUserModelEx(),
      myFixture.getSecurityContext(),
      myFixture.getProjectManager(),
      new BitbucketCloudBuildNameProvider()
    );

    @SuppressWarnings("deprecation") final CommitStatusPublisherSettings stashSettings =
      new StashSettings(new MockPluginDescriptor(), myWebLinks, problems, trustStoreProvider, myFixture.getSingletonService(OAuthConnectionsManager.class),
                        myFixture.getSingletonService(OAuthTokensStorage.class), myFixture.getUserModel(), myFixture.getSecurityContext(), myFixture.getProjectManager(),
                        new StashBuildNameProvider());

    @SuppressWarnings("deprecation") final CommitStatusPublisherSettings tfsPublisherSettings =
      new TfsPublisherSettings(new MockPluginDescriptor(), myWebLinks, problems, myFixture.getSingletonService(OAuthConnectionsManager.class),
                               myFixture.getSingletonService(OAuthTokensStorage.class), myFixture.getSecurityContext(), myFixture.getUserModel(), trustStoreProvider,
                               new TfsBuildNameProvider(), myFixture.getProjectManager());

    myServer.registerExtension(CommitStatusPublisherSettings.class, "gitHubSettings", gitHubSettings);
    myServer.registerExtension(CommitStatusPublisherSettings.class, "gitlabSettings", gitlabSettings);
    myServer.registerExtension(CommitStatusPublisherSettings.class, "bitbucketCloudSettings", bitbucketCloudSettings);
    myServer.registerExtension(CommitStatusPublisherSettings.class, "stashSettings", stashSettings);
    myServer.registerExtension(CommitStatusPublisherSettings.class, "tfsPublisherSettings", tfsPublisherSettings);

  }
}
