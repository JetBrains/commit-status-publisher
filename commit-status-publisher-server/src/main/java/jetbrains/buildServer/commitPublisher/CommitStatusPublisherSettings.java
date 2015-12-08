package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
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

  @Nullable
  CommitStatusPublisher createPublisher(@NotNull Map<String, String> params);

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params);

  @Nullable
  public PropertiesProcessor getParametersProcessor();

  boolean isEnabled();
}
