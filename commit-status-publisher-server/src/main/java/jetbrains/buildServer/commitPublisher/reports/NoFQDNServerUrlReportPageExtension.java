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

package jetbrains.buildServer.commitPublisher.reports;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class NoFQDNServerUrlReportPageExtension extends HealthStatusItemPageExtension {

  public NoFQDNServerUrlReportPageExtension(@NotNull PagePlaces pagePlaces,
                                            @NotNull NoFQDNServerUrlReport report,
                                            @NotNull PluginDescriptor pluginDescriptor) {
    super(report.getType(), pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("reports/noFQDNServerUrlReport.jsp"));
    register();
  }


  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    HealthStatusItem item = getStatusItem(request);
    model.putAll(item.getAdditionalData());
  }
}
