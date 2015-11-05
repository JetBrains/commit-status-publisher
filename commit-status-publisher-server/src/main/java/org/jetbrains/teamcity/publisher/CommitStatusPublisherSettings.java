package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public interface CommitStatusPublisherSettings {

  @NotNull
  String getId();

  @NotNull
  String getName();

  @Nullable
  String getEditSettingsUrl();

  /**
   * Get the names of any parameters that must be set.
   */
  @Nullable
  Collection<String> getMandatoryParameters();

  @Nullable
  Map<String, String> getDefaultParameters();

  /**
   * Get any changes necessary to make the supplied parameters valid.
   *
   * The supplied parameters map is not changed.
   *
   * There may not always be a known update for an invalid parameter, so the
   * parameters could still be invalid after applying the changes.
   *
   * @param params
   * @return Only the *changes* we need to make to params (if any).
   */
  @Nullable
  Map<String, String> getParameterUpgrades(@NotNull Map<String, String> params);

  @Nullable
  CommitStatusPublisher createPublisher(@NotNull Map<String, String> params);

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params);

  @Nullable
  public PropertiesProcessor getParametersProcessor();

}
