

package jetbrains.buildServer.commitPublisher;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DummyPublisherSettings implements CommitStatusPublisherSettings {
  static final String ID = "--";

  @NotNull
  public String getId() {
    return ID;
  }

  @NotNull
  public String getName() {
    return "--Choose publisher--";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return null;
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return null;
  }

  @Nullable
  @Override
  public CommitStatusPublisher createFeaturelessPublisher(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot) {
    return null;
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    return null;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    return null;
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "";
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    return null;
  }

  @NotNull
  @Override
  public List<OAuthConnectionDescriptor> getOAuthConnections(final @NotNull SProject project, final @NotNull SUser user) {
    return Collections.emptyList();
  }

  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return true;
  }

  @Override
  public boolean isEventSupported(final CommitStatusPublisher.Event event, final SBuildType buildType, final Map<String, String> params) {
    return false;
  }

  @Override
  public boolean isTestConnectionSupported() {
    return false;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    // does nothing
  }

  @Nullable
  @Override
  public KeyStore trustStore() {
    return null;
  }

  @Override
  public boolean isFeatureLessPublishingSupported(@NotNull SBuildType buildType) {
    return false;
  }

  @Override
  public boolean allowsFeatureLessPublishingForDependencies(@NotNull SBuildType buildType) {
    return false;
  }

  @Override
  public boolean isPublishingQueuedStatusEnabled(@NotNull SBuildType buildType) {
    return true;
  }
}