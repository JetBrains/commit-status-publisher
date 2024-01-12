

package jetbrains.buildServer.commitPublisher.reports;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class MissingSettingsReportPageExtension extends HealthStatusItemPageExtension {

  public MissingSettingsReportPageExtension(@NotNull PagePlaces pagePlaces,
                                            @NotNull MissingSettingsReport report,
                                            @NotNull PluginDescriptor pluginDescriptor) {
    super(report.getType(), pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("reports/missingSettingsReport.jsp"));
    register();
  }


  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    HealthStatusItem item = getStatusItem(request);
    model.putAll(item.getAdditionalData());
  }
}