package jetbrains.buildServer.commitPublisher.configuration;

import java.util.Collection;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcshostings.features.AuthTypeAwareBuildFeatureBuilder;
import jetbrains.buildServer.vcshostings.features.VcsHostingFeatureException;
import org.jetbrains.annotations.NotNull;

public abstract class CommitStatusPublisherFeatureBuilder extends AuthTypeAwareBuildFeatureBuilder<CommitStatusPublisherFeatureBuilder> {

  @NotNull
  private final CommitStatusPublisherSettings mySettings;

  protected CommitStatusPublisherFeatureBuilder(@NotNull CommitStatusPublisherSettings settings) {
    super(CommitStatusPublisherFeature.TYPE);
    mySettings = settings;
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder withVcsRoot(@NotNull SVcsRoot vcsRoot) {
    putParameter(Constants.VCS_ROOT_ID_PARAM, vcsRoot.getExternalId());
    return self();
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder withAllVcsRoots() {
    clearParameter(Constants.VCS_ROOT_ID_PARAM);
    return self();
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder withUrl(@NotNull String url) {
    throw new VcsHostingFeatureException("Custom service URL is not suppored by " + getClass().getSimpleName());
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder withPersonalToken(@NotNull String token) {
      throw new VcsHostingFeatureException("Personal token authentication is not supported by " + getClass().getSimpleName());
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder withPassword(@NotNull String username, @NotNull String password) {
      throw new VcsHostingFeatureException("Password authentication is not supported by " + getClass().getSimpleName());
  }

  @NotNull
  public CommitStatusPublisherFeatureBuilder withEarlyFailure(boolean publishEarlyFailure) {
    putParameter(Constants.PUBLISH_EARLY_FAILURE_PARAM, Boolean.toString(publishEarlyFailure));
    return self();
  }

  @Override
  public void validate(@NotNull BuildTypeIdentity buildType) {
    final PropertiesProcessor parametersProcessor = mySettings.getParametersProcessor(buildType);
    if (parametersProcessor == null) {
      throw new VcsHostingFeatureException("Unable to validate requested configuration, no parameters processor for " + mySettings.getName());
    }

    final Collection<InvalidProperty> invalidProperties = parametersProcessor.process(myParameters);
    if (!invalidProperties.isEmpty()) {
      throw new VcsHostingFeatureException("Validation failed for " + mySettings.getName() + ": " + invalidProperties);
    }
  }
}
