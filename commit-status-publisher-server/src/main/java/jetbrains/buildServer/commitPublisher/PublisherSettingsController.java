package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.controllers.*;
import jetbrains.buildServer.controllers.admin.projects.EditBuildTypeFormFactory;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PublisherSettingsController extends BaseController {

  private final String myUrl;
  private final PublisherManager myPublisherManager;
  private final ProjectManager myProjectManager;
  private final static Logger LOG = Logger.getInstance(PublisherSettingsController.class.getName());


  public PublisherSettingsController(@NotNull WebControllerManager controllerManager,
                                     @NotNull PluginDescriptor descriptor,
                                     @NotNull PublisherManager publisherManager,
                                     @NotNull ProjectManager projectManager) {
    myUrl = descriptor.getPluginResourcesPath("publisherSettings.html");
    myPublisherManager = publisherManager;
    controllerManager.registerController(myUrl, this);
    myProjectManager = projectManager;
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

    String projectId = request.getParameter("projectId");
    SProject project = myProjectManager.findProjectByExternalId(projectId);
    request.setAttribute("projectId", projectId);
    request.setAttribute("project", project);

    CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
    if (settings == null)
      return null;

    String settingsUrl = settings.getEditSettingsUrl();
    Map<String, String> params = settings.getDefaultParameters() != null ? settings.getDefaultParameters() : Collections.<String, String>emptyMap();
    if (Constants.TEST_CONNECTION_YES.equals(request.getParameter(Constants.TEST_CONNECTION_PARAM))) {
      processTestConnectionRequest(request, response, settings, params);
      return null;
    }

    request.setAttribute("propertiesBean", new BasePropertiesBean(params));
    request.setAttribute("currentUser", SessionUser.getUser(request));
    request.setAttribute("testConnectionSupported", settings.isTestConnectionSupported());

    SUser user = SessionUser.getUser(request);
    request.setAttribute("oauthConnections", settings.getOAuthConnections(project, user));

    if (settingsUrl != null)
      request.getRequestDispatcher(settingsUrl).include(request, response);

    return null;
  }

  @NotNull
  private BuildTypeIdentity getBuildTypeOrTemplate(String id) throws PublisherException {
    if (null == id)
      throw new PublisherException("No build type/template has been submitted");
    BuildTypeIdentity buildTypeOrTemplate;
    if (id.startsWith(EditBuildTypeFormFactory.BT_PREFIX)) {
      String btId = id.substring(EditBuildTypeFormFactory.BT_PREFIX.length());
      buildTypeOrTemplate = myProjectManager.findBuildTypeByExternalId(btId);
      if (null == buildTypeOrTemplate) {
        throw new PublisherException(String.format("No build type with id '%s' has been found", btId));
      }
    } else if (id.startsWith(EditBuildTypeFormFactory.TEMPLATE_PREFIX)) {
      String tplId = id.substring(EditBuildTypeFormFactory.TEMPLATE_PREFIX.length());
      buildTypeOrTemplate = myProjectManager.findBuildTypeTemplateByExternalId(tplId);
      if (null == buildTypeOrTemplate) {
        throw new PublisherException(String.format("No template with id '%s' has been found", tplId));
      }
    } else {
      throw new PublisherException(String.format("Malformed build type/teplate id parameter '%s'", id));
    }
    return buildTypeOrTemplate;
  }

  private void processTestConnectionRequest(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final CommitStatusPublisherSettings settings, @NotNull final Map<String, String> params) {
    new AjaxRequestProcessor().processRequest(request, response, new AjaxRequestProcessor.RequestHandler() {
      public void handleRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
        XmlResponseUtil.writeTestResult(xmlResponse, "");
        try {
          BasePropertiesBean propBean = new BasePropertiesBean(params);
          PluginPropertiesUtil.bindPropertiesFromRequest(request, propBean);
          Map<String, String> props = propBean.getProperties();
          PropertiesProcessor processor = settings.getParametersProcessor();
          if (null != processor) {
            processor.process(props);
          }
          testConnection(settings, props, request);
        } catch (PublisherException ex) {
          StringBuffer msgBuf = new StringBuffer(ex.getMessage());
          Throwable cause = ex.getCause();
          if (null != cause) {
            msgBuf.append(String.format(": %s", cause.getMessage()));
          }
          final String msg = msgBuf.toString();
          LOG.debug("Test connection failure", ex);
          XmlResponseUtil.writeErrors(xmlResponse, new ActionErrors() {{ addError("testConnectionFailed", msg); }});
        }
      }
    });
  }

  private static Map<String, String> resolveProperties(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull Map<String, String> params) {
    if (buildTypeOrTemplate instanceof ParametersSupport) {
      ValueResolver valueResolver = ((ParametersSupport)buildTypeOrTemplate).getValueResolver();
      return valueResolver.resolve(params);
    }
    return new HashMap<String, String>(params);
  }

  private static VcsRoot getVcsRootInstanceIfPossible(BuildTypeIdentity buildTypeOrTemplate, SVcsRoot sVcsRoot) {
    if (buildTypeOrTemplate instanceof SBuildType) {
      return ((SBuildType)buildTypeOrTemplate).getVcsRootInstanceForParent(sVcsRoot);
    } else {
      return sVcsRoot;
    }
  }

  private void testConnection(CommitStatusPublisherSettings settings, Map<String, String> params, @NotNull final HttpServletRequest request) throws PublisherException {
    BuildTypeIdentity buildTypeOrTemplate = getBuildTypeOrTemplate(request.getParameter("id"));
    Map<String, String> resolvedProperties = resolveProperties(buildTypeOrTemplate, params);

    String vcsRootId = resolvedProperties.get(Constants.VCS_ROOT_ID_PARAM);

    if ((null == vcsRootId || vcsRootId.isEmpty())) {
      List<SVcsRoot> roots = null;
      if (buildTypeOrTemplate instanceof BuildTypeSettings) {
        roots = ((BuildTypeSettings) buildTypeOrTemplate).getVcsRoots();
      }
      if (null == roots || roots.isEmpty()) {
        throw new PublisherException("No VCS roots attached");
      }

      for (SVcsRoot sVcsRoot: roots) {
        VcsRoot vcsRoot = getVcsRootInstanceIfPossible(buildTypeOrTemplate, sVcsRoot);
        settings.testConnection(buildTypeOrTemplate, vcsRoot, resolvedProperties);
      }
    } else {
      SVcsRoot sVcsRoot = myProjectManager.findVcsRootByExternalId(vcsRootId);
      if (null == sVcsRoot) {
        try {
          Long internalId = Long.valueOf(vcsRootId);
          sVcsRoot = myProjectManager.findVcsRootById(internalId);
        } catch (NumberFormatException ex) {
          throw new PublisherException(String.format("Unknown VCS root id '%s'", vcsRootId));
        }
      }
      if (null == sVcsRoot) {
        throw new PublisherException(String.format("VCS root not found for id '%s'", vcsRootId));
      }
      VcsRoot vcsRoot = getVcsRootInstanceIfPossible(buildTypeOrTemplate, sVcsRoot);
      settings.testConnection(buildTypeOrTemplate, vcsRoot, resolvedProperties);
    }
  }
}
