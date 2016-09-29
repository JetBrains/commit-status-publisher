package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;

public abstract class BasePublisherSettings implements CommitStatusPublisherSettings {

  protected final PluginDescriptor myDescriptor;
  protected final WebLinks myLinks;
  protected final ExecutorServices myExecutorServices;

  public BasePublisherSettings(@NotNull final ExecutorServices executorServices,
                               @NotNull PluginDescriptor descriptor,
                               @NotNull WebLinks links) {
    myDescriptor = descriptor;
    myLinks= links;
    myExecutorServices = executorServices;
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

  public boolean isEnabled() {
    return true;
  }
}
