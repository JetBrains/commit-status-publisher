package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.BuildProblemsTicketManager;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcshostings.features.VcsHostingBuildFeature;
import jetbrains.buildServer.vcshostings.features.VcsHostingFeatureException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.MapEntry.entry;

public class AzureDevOpsCommitStatusPublisherFeatureBuilderTest extends BaseServerTestCase {
  private AzureDevOpsCommitStatusPublisherFeatureBuilder myAzureDevOpsFeatureBuilder;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    final CommitStatusPublisherProblems problems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));
    final SSLTrustStoreProvider trustStoreProvider = () -> null;

    final CommitStatusPublisherSettings settings =
      new TfsPublisherSettings(new MockPluginDescriptor(), myWebLinks, problems, myFixture.getSingletonService(OAuthConnectionsManager.class),
                               myFixture.getSingletonService(OAuthTokensStorage.class), myFixture.getSecurityContext(), myFixture.getUserModel(), trustStoreProvider,
                               new TfsBuildNameProvider(), myFixture.getProjectManager());
    myAzureDevOpsFeatureBuilder = new AzureDevOpsCommitStatusPublisherFeatureBuilder(settings);
  }

  @Test
  public void buildForAccessToken() {
    final String token = "my-token";

    final VcsHostingBuildFeature feature = myAzureDevOpsFeatureBuilder.withPersonalToken(token)
                                                                      .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("publisherId", "tfs"),
      entry("tfsAuthType", "token"),
      entry("secure:accessToken", token)
    );
  }

  @Test
  public void buildForCustomUrl() {
    final String token = "my-token";
    final String url = "my.azure.local";

    final VcsHostingBuildFeature feature = myAzureDevOpsFeatureBuilder.withUrl(url)
                                                                      .withPersonalToken(token)
                                                                      .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("publisherId", "tfs"),
      entry("tfsServerUrl", url),
      entry("tfsAuthType", "token"),
      entry("secure:accessToken", token)
    );
  }

  @Test
  public void buildForPublishPullRequests() {
    final String token = "my-token";
    final boolean publishPullRequests = true;

    final VcsHostingBuildFeature feature = myAzureDevOpsFeatureBuilder.withPublishPullRequestStatuses(publishPullRequests)
                                                                      .withPersonalToken(token)
                                                                      .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("publisherId", "tfs"),
      entry("publish.pull.requests", Boolean.toString(publishPullRequests)),
      entry("tfsAuthType", "token"),
      entry("secure:accessToken", token)
    );
  }

  @Test
  public void buildForStoredToken() {
    final String tokenId = "my-token";

    final VcsHostingBuildFeature feature = myAzureDevOpsFeatureBuilder.withStoredToken(tokenId)
                                                                      .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("publisherId", "tfs"),
      entry("tfsAuthType", "storedToken"),
      entry("tokenId", tokenId)
    );
  }

  @Test(expectedExceptions = VcsHostingFeatureException.class, expectedExceptionsMessageRegExp = "Password authentication is not supported by AzureDevOpsCommitStatusPublisherFeatureBuilder")
  public void buildForPassword_not_supported() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    myBuildType.addVcsRoot(vcsRoot);

    myAzureDevOpsFeatureBuilder.withPassword("username", "password")
                               .withVcsRoot(vcsRoot)
                               .build(myBuildType);
  }
}
