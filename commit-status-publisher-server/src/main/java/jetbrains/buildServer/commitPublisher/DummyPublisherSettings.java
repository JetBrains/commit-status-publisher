package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DummyPublisherSettings implements CommitStatusPublisherSettings {
  public static final String ID = "--";

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
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
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

  public boolean isEnabled() {
    return true;
  }
}
