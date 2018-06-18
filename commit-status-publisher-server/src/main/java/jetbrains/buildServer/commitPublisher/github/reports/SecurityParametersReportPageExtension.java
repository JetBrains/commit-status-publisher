package jetbrains.buildServer.commitPublisher.github.reports;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public class SecurityParametersReportPageExtension extends HealthStatusItemPageExtension {

  public SecurityParametersReportPageExtension(@NotNull PagePlaces pagePlaces,
                                               @NotNull SecurityParametersReport report,
                                               @NotNull PluginDescriptor pluginDescriptor) {
    super(report.getType(), pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("github/reports/securityParametersReport.jsp"));
    register();
  }


  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    HealthStatusItem item = getStatusItem(request);
    model.putAll(item.getAdditionalData());
  }

  @Override
  public boolean isAvailable(@NotNull final HttpServletRequest request) {
    if (!super.isAvailable(request)) return false;

    HealthStatusItem item = getStatusItem(request);
    SBuildType bt = (SBuildType)item.getAdditionalData().get("buildType");
    if (bt == null) return false;

    return SessionUser.getUser(request).isPermissionGrantedForProject(bt.getProjectId(), Permission.VIEW_BUILD_CONFIGURATION_SETTINGS);
  }
}
