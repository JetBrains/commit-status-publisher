

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.*;
import jetbrains.buildServer.controllers.admin.projects.EditBuildTypeFormFactory;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.parameters.NullValueResolver;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
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

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class PublisherSettingsController extends BaseController {

  private final String myUrl;
  private final PublisherManager myPublisherManager;
  private final ProjectManager myProjectManager;

  @NotNull
  private final SecurityContext mySecurityContext;

  private static final String ILLEGAL_ACCESS_WARNING = "User %s is not allowed to edit the build configuration %s";


  public PublisherSettingsController(@NotNull WebControllerManager controllerManager,
                                     @NotNull PluginDescriptor descriptor,
                                     @NotNull PublisherManager publisherManager,
                                     @NotNull ProjectManager projectManager,
                                     @NotNull SecurityContext securityContext) {
    myUrl = descriptor.getPluginResourcesPath("publisherSettings.html");
    myPublisherManager = publisherManager;
    controllerManager.registerController(myUrl, this);
    myProjectManager = projectManager;
    mySecurityContext = securityContext;
  }

  public String getUrl() {
    return myUrl;
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    SUser user = SessionUser.getUser(request);
    if (user == null)
      return null;

    String buildTypeId = request.getParameter("id");
    if (buildTypeId != null) {
      BuildTypeIdentity buildTypeOrTemplate;
      try {
        buildTypeOrTemplate = getBuildTypeOrTemplate(buildTypeId);
      } catch (PublisherException e) {
        LOG.warn(String.format(ILLEGAL_ACCESS_WARNING, user.getUsername(), buildTypeId));
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return null;
      }

      if (!user.isPermissionGrantedForProject(buildTypeOrTemplate.getProject().getProjectId(), Permission.EDIT_PROJECT)) {
        LOG.warn(String.format(ILLEGAL_ACCESS_WARNING, user.getUsername(), buildTypeId));
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return null;
      }
    }

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
    Map<String, String> params = settings.getDefaultParameters() != null ? settings.getDefaultParameters() : Collections.emptyMap();
    if (Constants.TEST_CONNECTION_YES.equals(request.getParameter(Constants.TEST_CONNECTION_PARAM))) {
      processTestConnectionRequest(request, response, settings, params);
      return null;
    }

    if (project != null) {
      settings.getSpecificAttributes(project, Collections.emptyMap()).forEach((k, v) -> request.setAttribute(k, v));
    }

    request.setAttribute("propertiesBean", new BasePropertiesBean(params));
    request.setAttribute("currentUser", SessionUser.getUser(request));
    request.setAttribute("testConnectionSupported", settings.isTestConnectionSupported());

    if (project != null && user != null) {
      List<OAuthConnectionDescriptor> oauthConnections = settings.getOAuthConnections(project, user);
      request.setAttribute("oauthConnections", oauthConnections);
      request.setAttribute("refreshTokenSupported", oauthConnections.stream().anyMatch(c -> c.getOauthProvider().isTokenRefreshSupported()));
      request.setAttribute("canEditProject", AuthUtil.hasPermissionToManageProject(mySecurityContext.getAuthorityHolder(), project.getProjectId()));
    }

    if (settingsUrl != null) {
      return new ModelAndView(settingsUrl);
    }

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
        ActionErrors errors = new ActionErrors();
        try {
          BasePropertiesBean propBean = new BasePropertiesBean(params);
          PluginPropertiesUtil.bindPropertiesFromRequest(request, propBean);
          Map<String, String> props = propBean.getProperties();
          BuildTypeIdentity buildTypeOrTemplate = getBuildTypeOrTemplate(request.getParameter("id"));
          PropertiesProcessor processor = settings.getParametersProcessor(buildTypeOrTemplate);
          if (null != processor) {
            Collection<InvalidProperty> invalidProps = processor.process(props);
            if (invalidProps != null) {
              for (InvalidProperty prop : invalidProps) {
                errors.addError("testConnectionFailed", prop.getInvalidReason());
              }
            }
          }
          if (!errors.hasErrors()) {
            testConnection(settings, props, request, errors);
          }
        } catch (PublisherException ex) {
          reportTestConnectionFailure(ex, errors);
        }
        if (errors.hasErrors()) {
          XmlResponseUtil.writeErrors(xmlResponse, errors);
        }
      }
    });
  }

  private void reportTestConnectionFailure(@NotNull PublisherException ex, @NotNull ActionErrors errors) {
    StringBuffer msgBuf = new StringBuffer(ex.getMessage());
    Throwable cause = ex.getCause();
    if (null != cause) {
      msgBuf.append(String.format(": %s", cause.getMessage()));
    }
    final String msg = msgBuf.toString();
    LOG.debug("Test connection failure", ex);
    errors.addError("testConnectionFailed", msg);
  }

  private static Map<String, String> resolveProperties(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull Map<String, String> params) {
    return getValueResolver(buildTypeOrTemplate).resolve(params);
  }

  private static VcsRoot getResolvingVcsRoot(BuildTypeIdentity buildTypeOrTemplate, SVcsRoot sVcsRoot) {
    if (buildTypeOrTemplate instanceof SBuildType) {
      return ((SBuildType)buildTypeOrTemplate).getVcsRootInstanceForParent(sVcsRoot);
    } else {
      return new VcsRootResolvingWrapper(getValueResolver(buildTypeOrTemplate), sVcsRoot);
    }
  }

  private void testConnection(CommitStatusPublisherSettings settings, Map<String, String> params, @NotNull final HttpServletRequest request,
                              @NotNull ActionErrors errors) throws PublisherException {
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

      boolean isTested = false;
      for (SVcsRoot sVcsRoot: roots) {
        try {
          VcsRoot vcsRoot = getResolvingVcsRoot(buildTypeOrTemplate, sVcsRoot);
          if (settings.isPublishingForVcsRoot(vcsRoot)) {
            isTested = true;
            settings.testConnection(buildTypeOrTemplate, vcsRoot, resolvedProperties);
          }
        } catch (PublisherException ex) {
          reportTestConnectionFailure(ex, errors);
        }
      }
      if (!isTested) {
        throw new PublisherException("No relevant VCS roots attached");
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
      VcsRoot vcsRoot = getResolvingVcsRoot(buildTypeOrTemplate, sVcsRoot);
      settings.testConnection(buildTypeOrTemplate, vcsRoot, resolvedProperties);
    }
  }

  @NotNull
  private static ValueResolver getValueResolver(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
    if (buildTypeOrTemplate instanceof ParametersSupport) {
      return ((ParametersSupport)buildTypeOrTemplate).getValueResolver();
    }
    return new NullValueResolver();
  }
}