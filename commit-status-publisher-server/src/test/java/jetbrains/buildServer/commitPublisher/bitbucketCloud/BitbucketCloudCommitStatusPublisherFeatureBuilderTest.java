package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.BuildProblemsTicketManager;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcshostings.features.VcsHostingBuildFeature;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.MapEntry.entry;

public class BitbucketCloudCommitStatusPublisherFeatureBuilderTest extends BaseServerTestCase {

  private BitbucketCloudCommitStatusPublisherFeatureBuilder myBitbucketCloudFeatureBuilder;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    final CommitStatusPublisherProblems problems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));
    final SSLTrustStoreProvider trustStoreProvider = () -> null;

    @SuppressWarnings("deprecation") final CommitStatusPublisherSettings settings = new BitbucketCloudSettings(
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
    myBitbucketCloudFeatureBuilder = new BitbucketCloudCommitStatusPublisherFeatureBuilder(settings);
  }

  @Test
  public void buildForPassword() {
    final String username = "my-username";
    final String password = "my-password";

    final VcsHostingBuildFeature feature = myBitbucketCloudFeatureBuilder.withPassword(username, password).build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("publisherId", "bitbucketCloudPublisher"),
      entry("authType", "password"),
      entry("bitbucketUsername", username),
      entry("secure:bitbucketPassword", password));
  }
}