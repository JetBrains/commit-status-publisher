package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface CommitStatusPublisherSettings {

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

  boolean isEnabled();
}
