package jetbrains.buildServer.commitPublisher.github;

import com.google.common.collect.ImmutableMap;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.commitPublisher.github.api.impl.HttpClientWrapperImpl;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.BuildProblemsTicketManager;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcshostings.features.VcsHostingBuildFeature;
import jetbrains.buildServer.vcshostings.features.VcsHostingFeatureException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;

public class GitHubCommitStatusPublisherFeatureBuilderTest extends BaseServerTestCase {

  private GitHubCommitStatusPublisherFeatureBuilder myGitHubFeatureBuilder;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    @SuppressWarnings("deprecation") final ChangeStatusUpdater changeStatusUpdater =
      new ChangeStatusUpdater(new GitHubApiFactoryImpl(new HttpClientWrapperImpl(new HTTPRequestBuilder.ApacheClient43RequestHandler(), () -> null),
                                                       myFixture.getSingletonService(OAuthTokensStorage.class),
                                                       myFixture.getSingletonService(OAuthConnectionsManager.class),
                                                       myFixture.getProjectManager()), myFixture.getVcsHistory());

    final CommitStatusPublisherProblems problems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));

    final SSLTrustStoreProvider trustStoreProvider = () -> null;

    final GitHubBuildContextProvider buildNameProvider = new GitHubBuildContextProvider();

    @SuppressWarnings("deprecation") final GitHubSettings settings = new GitHubSettings(changeStatusUpdater, new MockPluginDescriptor(), myWebLinks, problems,
                                                                                        myFixture.getSingletonService(OAuthConnectionsManager.class),
                                                                                        myFixture.getSingletonService(OAuthTokensStorage.class),
                                                                                        myFixture.getSecurityContext(),
                                                                                        trustStoreProvider,
                                                                                        buildNameProvider);

    myGitHubFeatureBuilder = new GitHubCommitStatusPublisherFeatureBuilder(settings);
  }

  @Test
  public void buildForPersonalToken() {
    final VcsHostingBuildFeature feature = myGitHubFeatureBuilder.withPersonalToken("my-token")
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("github_host", "https://api.github.com"),
      entry("github_authentication_type", "token"),
      entry("secure:github_access_token", "my-token"),
      entry("publisherId","githubStatusPublisher")
    );
  }

  @Test
  public void buildForPersonalToken_custom_url() {
    final String customUrl = "https://my.github.local/api";
    final VcsHostingBuildFeature feature = myGitHubFeatureBuilder.withPersonalToken("my-token")
                                                                 .withUrl(customUrl)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("github_host", customUrl),
      entry("github_authentication_type", "token"),
      entry("secure:github_access_token", "my-token"),
      entry("publisherId","githubStatusPublisher")
    );
  }

  @Test
  public void buildForPassword() {
    final String username = "user";
    final String password = "pass";
    final VcsHostingBuildFeature feature = myGitHubFeatureBuilder.withPassword(username, password)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("github_host", "https://api.github.com"),
      entry("github_authentication_type", "password"),
      entry("github_username", username),
      entry("secure:github_password", password),
      entry("publisherId","githubStatusPublisher")
    );
  }

  @Test
  public void buildForVcsRootAuthentication_allRoots() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);

    final VcsHostingBuildFeature feature = myGitHubFeatureBuilder.withVcsRootAuthentication()
                                                                 .withAllVcsRoots()
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("github_host", "https://api.github.com"),
      entry("github_authentication_type", "vcsRoot"),
      entry("publisherId","githubStatusPublisher")
    );
  }

  @Test
  public void buildForVcsRootAuthentication_singleRoot() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);

    final VcsHostingBuildFeature feature = myGitHubFeatureBuilder.withVcsRootAuthentication()
                                                                 .withVcsRoot(vcsRoot)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("github_host", "https://api.github.com"),
      entry("github_authentication_type", "vcsRoot"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("publisherId","githubStatusPublisher")
    );
  }

  @Test(expectedExceptions = VcsHostingFeatureException.class, expectedExceptionsMessageRegExp = ".*No VCS Roots attached.*")
  public void buildForVcsRootAuthentication_missingRoot() {
    myGitHubFeatureBuilder.withVcsRootAuthentication()
                          .build(myBuildType);
  }

  @Test
  public void buildForStoredToken() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    myBuildType.addVcsRoot(vcsRoot);
    final String tokenId = "tokenId";
    final VcsHostingBuildFeature feature = myGitHubFeatureBuilder.withStoredToken(tokenId)
                                                                 .withVcsRoot(vcsRoot)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("github_host", "https://api.github.com"),
      entry("github_authentication_type", "storedToken"),
      entry("vcsRootId", vcsRoot.getExternalId()),
      entry("tokenId", tokenId),
      entry("publisherId","githubStatusPublisher")
    );
  }
}