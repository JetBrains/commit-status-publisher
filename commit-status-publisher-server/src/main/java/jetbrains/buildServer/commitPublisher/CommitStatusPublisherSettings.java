package jetbrains.buildServer.commitPublisher;

import java.security.KeyStore;
import jetbrains.buildServer.TeamCityExtension;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;

import java.util.Map;

public interface CommitStatusPublisherSettings extends TeamCityExtension {

  @NotNull
  String getId();

  @NotNull
  String getName();

  @Nullable
  String getEditSettingsUrl();

  @Nullable
  Map<String, String> getDefaultParameters();

  /**
   * Transforms parameters of the publisher before they are shown in UI
   * @param params parameters to transform
   * @return map of transformed parameters or null if no transformation is needed
   */
  @Nullable
  Map<String, String> transformParameters(@NotNull Map<String, String> params);

  @Nullable
  CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params);

  @NotNull
  String describeParameters(@NotNull Map<String, String> params);

  @Nullable
  PropertiesProcessor getParametersProcessor();

  @NotNull
  Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final SProject project, final SUser user);

  boolean isEnabled();

  boolean isPublishingForVcsRoot(VcsRoot vcsRoot);

  public boolean isEventSupported(Event event, final SBuildType buildType, final Map<String, String> params);

  boolean isTestConnectionSupported();

  default boolean isFQDNTeamCityUrlRequired() { return false; }

  void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException;

  @Nullable
  default public String getServerVersion(@NotNull String url) {
    return null;
  }

  @Nullable
  KeyStore trustStore();
}
