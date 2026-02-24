package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.configuration.CommitStatusPublisherFeatureBuilder;
import org.jetbrains.annotations.NotNull;

public class AzureDevOpsCommitStatusPublisherFeatureBuilder extends CommitStatusPublisherFeatureBuilder {
  protected AzureDevOpsCommitStatusPublisherFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    super(settings);
  }

  @NotNull
  @Override
  public AzureDevOpsCommitStatusPublisherFeatureBuilder withUrl(@NotNull String url) {
    withParameter(TfsConstants.SERVER_URL, url);
    return this;
  }

  @NotNull
  @Override
  public CommitStatusPublisherFeatureBuilder withPublishPullRequestStatuses(boolean publishPullRequestStatuses) {
    withParameter(TfsConstants.PUBLISH_PULL_REQUESTS, Boolean.toString(publishPullRequestStatuses));
    return this;
  }

  @NotNull
  @Override
  public AzureDevOpsCommitStatusPublisherFeatureBuilder withPersonalToken(@NotNull String token) {
    clearAuthParameters();
    putParameter(TfsConstants.AUTHENTICATION_TYPE, Constants.AUTH_TYPE_ACCESS_TOKEN);
    putParameter(TfsConstants.ACCESS_TOKEN, token);
    return this;
  }

  @Override
  protected void clearAuthParameters() {
    super.clearAuthParameters();
    clearParameter(TfsConstants.ACCESS_TOKEN);
  }

  @NotNull
  @Override
  protected String authTypeParameterName() {
    return TfsConstants.AUTHENTICATION_TYPE;
  }
}
