package jetbrains.buildServer.commitPublisher.reports;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class UnconditionalPublishingReportPageExtension extends HealthStatusItemPageExtension {


  public UnconditionalPublishingReportPageExtension(@NotNull PagePlaces pagePlaces,
                                                    @NotNull UnconditionalPublishingReport report,
                                                    @NotNull PluginDescriptor pluginDescriptor) {
    super(report.getType(), pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("reports/unconditionalPublishingReport.jsp"));
    register();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    final HealthStatusItem statusItem = getStatusItem(request);
    model.putAll(statusItem.getAdditionalData());
  }
}
