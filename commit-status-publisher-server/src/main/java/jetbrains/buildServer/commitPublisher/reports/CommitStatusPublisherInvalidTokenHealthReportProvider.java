package jetbrains.buildServer.commitPublisher.reports;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.oauth.UnavailableFeatureTokenReportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommitStatusPublisherInvalidTokenHealthReportProvider implements UnavailableFeatureTokenReportProvider {

  public CommitStatusPublisherInvalidTokenHealthReportProvider(@NotNull ExtensionHolder extensionHolder) {
    extensionHolder.registerExtension(UnavailableFeatureTokenReportProvider.class, "commitStatusPublisherTokenReport", this);
  }

  @NotNull
  @Override
  public String getFeatureType() {
    return CommitStatusPublisherFeature.TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Commit Status Publisher";
  }

  @Override
  public boolean shouldBeAppliedTo(@NotNull SBuildFeatureDescriptor featureDescriptor) {
    String publisherId = featureDescriptor.getParameters().get(Constants.PUBLISHER_ID_PARAM);
    if (publisherId == null) {
      return false;
    }
    if (publisherId.equals(Constants.GITHUB_PUBLISHER_ID)) {
      final GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(featureDescriptor.getParameters().get(Constants.GITHUB_AUTH_TYPE));
      return authenticationType == GitHubApiAuthenticationType.STORED_TOKEN;
    } else {
      String authenticationType = featureDescriptor.getParameters().get(Constants.AUTH_TYPE);
      return Constants.AUTH_TYPE_STORED_TOKEN.equals(authenticationType);
    }
  }

  @Nullable
  @Override
  public String getTokenId(@NotNull SBuildFeatureDescriptor buildFeature) {
    return buildFeature.getParameters().get(Constants.TOKEN_ID);
  }
}
