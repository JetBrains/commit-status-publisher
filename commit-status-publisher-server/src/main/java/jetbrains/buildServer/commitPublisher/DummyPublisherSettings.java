package jetbrains.buildServer.commitPublisher;

import java.util.Collections;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
  public PropertiesProcessor getParametersProcessor() {
    return null;
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final SProject project, final SUser user) {
    return Collections.emptyMap();
  }

  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return true;
  }

  @Override
  public boolean isEventSupported(final CommitStatusPublisher.Event event) {
    return false;
  }

  @Override
  public boolean isTestConnectionSupported() {
    return false;
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) {
    // does nothing
  }
}
