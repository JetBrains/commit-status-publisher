package jetbrains.buildServer.commitPublisher.gitlab;

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
import jetbrains.buildServer.vcs.VcsModificationHistoryEx;
import jetbrains.buildServer.vcshostings.features.VcsHostingBuildFeature;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.MapEntry.entry;

public class GitLabCommitStatusPublisherFeatureBuilderTest extends BaseServerTestCase {

  private GitLabCommitStatusPublisherFeatureBuilder myGitLabFeatureBuilder;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    final VcsModificationHistoryEx history = myFixture.getVcsHistory();
    final CommitStatusPublisherProblems problems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildProblemsTicketManager.class));
    final SSLTrustStoreProvider trustStoreProvider = () -> null;

    @SuppressWarnings("deprecation") final CommitStatusPublisherSettings settings =
      new GitlabSettings(new MockPluginDescriptor(),
                         myWebLinks,
                         problems,
                         trustStoreProvider,
                         history,
                         myFixture.getSingletonService(OAuthConnectionsManager.class),
                         myFixture.getSingletonService(OAuthTokensStorage.class),
                         getUserModelEx(),
                         myFixture.getSecurityContext(),
                         myFixture);
    myGitLabFeatureBuilder = new GitLabCommitStatusPublisherFeatureBuilder(settings);
  }

  @Test
  public void buildForAccessToken() {
    final String token = "my-token";

    final VcsHostingBuildFeature feature = myGitLabFeatureBuilder.withPersonalToken(token)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("authType", "token"),
      entry("secure:gitlabAccessToken", token)
    );
  }

  @Test
  public void buildForCustomUrl() {
    final String token = "my-token";
    final String url = "my.gitlab.local";

    final VcsHostingBuildFeature feature = myGitLabFeatureBuilder.withUrl(url)
                                                                 .withPersonalToken(token)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("gitlabApiUrl", url),
      entry("authType", "token"),
      entry("secure:gitlabAccessToken", token)
    );
  }

  @Test
  public void buildForStoredToken() {
    final String tokenId = "my-token";

    final VcsHostingBuildFeature feature = myGitLabFeatureBuilder.withStoredToken(tokenId)
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


    final VcsHostingBuildFeature feature = myGitLabFeatureBuilder.withVcsRootAuthentication()
                                                                 .withVcsRoot(vcsRoot)
                                                                 .build(myBuildType);

    then(feature.getType()).isEqualTo("commit-status-publisher");
    then(feature.getParameters()).containsOnly(
      entry("authType", "vcsRoot"),
      entry("vcsRootId", vcsRoot.getExternalId())
    );
  }
}