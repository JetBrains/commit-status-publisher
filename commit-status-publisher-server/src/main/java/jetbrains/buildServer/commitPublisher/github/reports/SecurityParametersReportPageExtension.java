/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.github.reports;

import jetbrains.buildServer.serverSide.SBuildType;
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
