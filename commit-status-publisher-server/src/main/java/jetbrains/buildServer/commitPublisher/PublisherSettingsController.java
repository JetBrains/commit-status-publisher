package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Map;

public class PublisherSettingsController extends BaseController {

  private final String myUrl;
  private final PublisherManager myPublisherManager;

  public PublisherSettingsController(@NotNull WebControllerManager controllerManager,
                                     @NotNull PluginDescriptor descriptor,
                                     @NotNull PublisherManager publisherManager) {
    myUrl = descriptor.getPluginResourcesPath("publisherSettings.html");
    myPublisherManager = publisherManager;
    controllerManager.registerController(myUrl, this);
  }

  public String getUrl() {
    return myUrl;
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    String publisherId = request.getParameter(Constants.PUBLISHER_ID_PARAM);
    if (publisherId == null)
      return null;
    request.setAttribute("projectId", request.getParameter("projectId"));
    CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
    if (settings == null)
      return null;
    String settingsUrl = settings.getEditSettingsUrl();
    Map<String, String> params = settings.getDefaultParameters() != null ? settings.getDefaultParameters() : Collections.<String, String>emptyMap();
    request.setAttribute("propertiesBean", new BasePropertiesBean(params));
    request.setAttribute("currentUser", SessionUser.getUser(request));
    if (settingsUrl != null)
      request.getRequestDispatcher(settings.getEditSettingsUrl()).include(request, response);

    return null;
  }
}
