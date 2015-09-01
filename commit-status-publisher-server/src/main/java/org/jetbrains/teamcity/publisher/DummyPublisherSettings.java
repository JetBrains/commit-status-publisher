package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DummyPublisherSettings extends BaseCommitStatusSettings  {
  public static final String ID = "--";

  @NotNull
  public String getId() {
    return ID;
  }

  @NotNull
  public String getName() {
    return "--Select publisher--";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return null;
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
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
}
