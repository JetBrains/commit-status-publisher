package jetbrains.buildServer.commitPublisher.configuration;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.bitbucketCloud.BitbucketCloudCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.github.GitHubCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.commitPublisher.gitlab.GitLabCommitStatusPublisherFeatureBuilder;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;

public class CommitStatusPublisherFeatureBuilderFactoryTest extends BaseServerTestCase {

  private CommitStatusPublisherFeatureBuilderFactory myFactory;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFactory = new CommitStatusPublisherFeatureBuilderFactory(myServer);
  }

  @DataProvider
  public static Object[][] argsForCreateForConnection() {
    return new Object[][]{
      {"GitHub", "githubStatusPublisher", GitHubCommitStatusPublisherFeatureBuilder.class},
      {"GHE", "githubStatusPublisher", GitHubCommitStatusPublisherFeatureBuilder.class},
      {"BitBucketCloud", "bitbucketCloudPublisher",  BitbucketCloudCommitStatusPublisherFeatureBuilder.class},
      {"GitLabCom", "gitlabStatusPublisher", GitLabCommitStatusPublisherFeatureBuilder.class},
      {"GitLabCEorEE", "gitlabStatusPublisher", GitLabCommitStatusPublisherFeatureBuilder.class}
    };
  }

  @Test(dataProvider = "argsForCreateForConnection")
  public void createForConnection_ok(@NotNull String connectionType,
                                     @NotNull String publisherId,
                                     @NotNull Class<? extends CommitStatusPublisherFeatureBuilder> expectedBuilderType) {
    mockSettingsOfId(publisherId);
    final OAuthConnectionDescriptor connection = mockConnectionOfType(connectionType);
    final CommitStatusPublisherFeatureBuilder builder = myFactory.createForConnection(connection);
    assertInstanceOf(builder, expectedBuilderType);
  }

  @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "^Unsupported OAuth connection type: JetBrains Space$")
  public void createForConnection_unsupported_connection_type() {
    final OAuthConnectionDescriptor connection = mockConnectionOfType("JetBrains Space");
    myFactory.createForConnection(connection);
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "^publisher settings extension bean not found for publisher githubStatusPublisher$")
  public void createForConnection_missing_settings_bean() {
    final OAuthConnectionDescriptor connection = mockConnectionOfType("GitHub");
    myFactory.createForConnection(connection);
  }

  @NotNull
  private OAuthConnectionDescriptor mockConnectionOfType(@NotNull String type) {
    final OAuthConnectionDescriptor mockConnection = Mockito.mock(OAuthConnectionDescriptor.class);
    when(mockConnection.getProviderType()).thenReturn(type);

    return mockConnection;
  }

  private void mockSettingsOfId(@NotNull String publisherId) {
    final CommitStatusPublisherSettings mockSettings = Mockito.mock(CommitStatusPublisherSettings.class);
    when(mockSettings.getId()).thenReturn(publisherId);
    myServer.registerExtension(CommitStatusPublisherSettings.class, publisherId, mockSettings);
  }
}