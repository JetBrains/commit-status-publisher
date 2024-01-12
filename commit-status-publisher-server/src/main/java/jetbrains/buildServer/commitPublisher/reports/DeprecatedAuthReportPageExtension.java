

package jetbrains.buildServer.commitPublisher.reports;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class DeprecatedAuthReportPageExtension extends HealthStatusItemPageExtension {

  public DeprecatedAuthReportPageExtension(@NotNull PagePlaces pagePlaces,
                                           @NotNull DeprecatedAuthReport report,
                                           @NotNull PluginDescriptor pluginDescriptor) {
    super(report.getType(), pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("reports/deprecatedAuthReport.jsp"));
    register();
  }


  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    HealthStatusItem item = getStatusItem(request);
    model.putAll(item.getAdditionalData());
  }
}