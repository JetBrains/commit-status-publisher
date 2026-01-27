package jetbrains.buildServer.commitPublisher.stash;

import com.google.common.collect.ImmutableMap;
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
import org.assertj.core.api.Assertions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.MapEntry.entry;

public class BitbucketServerCommitStatusPublisherFeatureBuilderTest extends BaseServerTestCase {
  private BitbucketServerCommitStatusPublisherFeatureBuilder myBitbucketServerFeatureBuilder;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    final CommitStatusPublisherProblems problems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));
    final SSLTrustStoreProvider trustStoreProvider = () -> null;

    final CommitStatusPublisherSettings settings =
      new StashSettings(new MockPluginDescriptor(), myWebLinks, problems, trustStoreProvider, myFixture.getSingletonService(OAuthConnectionsManager.class),
                        myFixture.getSingletonService(OAuthTokensStorage.class), myFixture.getUserModel(), myFixture.getSecurityContext(), myFixture.getProjectManager(),
                        new StashBuildNameProvider());
    myBitbucketServerFeatureBuilder = new BitbucketServerCommitStatusPublisherFeatureBuilder(settings);
  }

  @Test
  public void buildForCustomUrl() {
    final String tokenId = "my-token";
    final String url = "my.bbserver.local";

    final VcsHostingBuildFeature feature = myBitbucketServerFeatureBuilder.withUrl(url)
                                                                          .withStoredToken(tokenId)
                                                                          .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("stashBaseUrl", url),
      entry("authType", "storedToken"),
      entry("tokenId", tokenId)
    );
  }

  @Test
  public void buildForPassword() {
    final String username = "user";
    final String password = "pass";
    final VcsHostingBuildFeature feature = myBitbucketServerFeatureBuilder.withPassword(username, password)
                                                                          .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      Assertions.entry("authType", "password"),
      Assertions.entry("stashUsername", username),
      Assertions.entry("secure:stashPassword", password)
    );
  }

  @Test
  public void buildForStoredToken() {
    final String tokenId = "my-token";

    final VcsHostingBuildFeature feature = myBitbucketServerFeatureBuilder.withStoredToken(tokenId)
                                                                          .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("authType", "storedToken"),
      entry("tokenId", tokenId)
    );
  }

  @Test
  public void buildForVcsRootAuthentication() {
    final SVcsRootEx vcsRoot = createVcsRoot("test", myProject);
    vcsRoot.setProperties(ImmutableMap.of("authMethod", "PASSWORD"));
    myBuildType.addVcsRoot(vcsRoot);


    final VcsHostingBuildFeature feature = myBitbucketServerFeatureBuilder.withVcsRootAuthentication()
                                                                          .withVcsRoot(vcsRoot)
                                                                          .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("authType", "vcsRoot"),
      Assertions.entry("vcsRootId", vcsRoot.getExternalId())
    );
  }
}
