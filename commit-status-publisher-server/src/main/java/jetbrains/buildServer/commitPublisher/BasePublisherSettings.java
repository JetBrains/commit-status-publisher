package jetbrains.buildServer.commitPublisher;

import com.google.gson.Gson;
import java.util.Collections;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;

public abstract class BasePublisherSettings implements CommitStatusPublisherSettings {

  protected final PluginDescriptor myDescriptor;
  protected final WebLinks myLinks;
  protected final ExecutorServices myExecutorServices;
  protected CommitStatusPublisherProblems myProblems;
  protected final Gson myGson = new Gson();

  public BasePublisherSettings(@NotNull final ExecutorServices executorServices,
                               @NotNull PluginDescriptor descriptor,
                               @NotNull WebLinks links,
                               @NotNull CommitStatusPublisherProblems problems) {
    myDescriptor = descriptor;
    myLinks= links;
    myExecutorServices = executorServices;
    myProblems = problems;
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
  @Override
  public String describeParameters(@NotNull final Map<String, String> params) {
    return String.format("Post commit status to %s", getName());
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final SProject project, final SUser user) {
    return Collections.emptyMap();
  }

  public boolean isEnabled() {
    return true;
  }

  public boolean isTestConnectionSupported() { return false; }

  public void testConnection(@NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    throw new UnsupportedOperationException(String.format("Test connection functionality is not supported by %s publisher", getName()));
  }
}
