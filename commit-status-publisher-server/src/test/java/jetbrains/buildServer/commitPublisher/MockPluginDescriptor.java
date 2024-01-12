

package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author anton.zamolotskikh, 18/11/16.
 */
public class MockPluginDescriptor implements PluginDescriptor {
  public String getParameterValue(@NotNull final String key) {
    return null;
  }

  @NotNull
  public String getPluginName() {
    return "";
  }

  @NotNull
  public String getPluginResourcesPath() {
    return "";
  }

  @NotNull
  public String getPluginResourcesPath(@NotNull final String relativePath) {
    return relativePath;
  }

  public String getPluginVersion() {
    return null;
  }

  @NotNull
  public File getPluginRoot() {
    return null;
  }
}