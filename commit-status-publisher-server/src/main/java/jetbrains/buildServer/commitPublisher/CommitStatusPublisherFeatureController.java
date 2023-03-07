/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

public class CommitStatusPublisherFeatureController extends BaseController {

  private final String myUrl;
  private final PluginDescriptor myDescriptor;
  private final PublisherManager myPublisherManager;
  private final PublisherSettingsController myPublisherSettingsController;
  private final ProjectManager myProjectManager;

  public CommitStatusPublisherFeatureController(@NotNull ProjectManager projectManager,
                                                @NotNull WebControllerManager controllerManager,
                                                @NotNull PluginDescriptor descriptor,
                                                @NotNull PublisherManager publisherManager,
                                                @NotNull PublisherSettingsController publisherSettingsController) {
    myProjectManager = projectManager;
    myDescriptor = descriptor;
    myPublisherManager = publisherManager;
    myPublisherSettingsController = publisherSettingsController;
    myUrl = descriptor.getPluginResourcesPath("commitStatusPublisherFeature.html");
    controllerManager.registerController(myUrl, this);
  }

  public String getUrl() {
    return myUrl;
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
    BasePropertiesBean props = (BasePropertiesBean) request.getAttribute("propertiesBean");
    String publisherId = props.getProperties().get(Constants.PUBLISHER_ID_PARAM);
    ModelAndView mv = publisherId != null ? createEditPublisherModel(publisherId) : createAddPublisherModel();
    CommitStatusPublisherSettings settings = null;
    if (publisherId != null) {
      settings = myPublisherManager.findSettings(publisherId);
      transformParameters(props, publisherId, mv);
    }
    mv.addObject("publisherSettingsUrl", myPublisherSettingsController.getUrl());
    mv.addObject("showMode", "popup");
    List<VcsRoot> vcsRoots = getVcsRoots(request);
    mv.addObject("vcsRoots", vcsRoots);
    Map <String, String> params = props.getProperties();

    if (params.containsKey(Constants.VCS_ROOT_ID_PARAM)) {
      Long internalId;
      String vcsRootId = params.get(Constants.VCS_ROOT_ID_PARAM);
      try {
        internalId = Long.valueOf(vcsRootId);
      } catch (NumberFormatException ex) {
        internalId = null;
      }
      SVcsRoot vcsRoot = null;
      for (VcsRoot vcs: vcsRoots) {
        if (!(vcs instanceof SVcsRoot)) continue;
        SVcsRoot candidate = (SVcsRoot) vcs;
        if (candidate.getExternalId().equals(vcsRootId)) {
          vcsRoot = candidate;
          String tokenId = vcsRoot.getProperty("tokenId");
          if (tokenId != null) {
            mv.addObject("tokenId", tokenId);
          }
          break;
        }
        if (null != internalId && internalId.equals(candidate.getId())) {
          props.setProperty(Constants.VCS_ROOT_ID_PARAM, candidate.getExternalId());
          vcsRoot = candidate;
          break;
        }
      }
      if(null == vcsRoot) {
        mv.addObject("hasMissingVcsRoot", true);
        if (null != internalId) {
          vcsRoot = myProjectManager.findVcsRootById(internalId);
        } else {
          vcsRoot = myProjectManager.findVcsRootByExternalId(vcsRootId);
        }
        if (null != vcsRoot) {
          mv.addObject("missingVcsRoot", vcsRoot);
        }
      }
    }
    SProject project = getProject(request);
    mv.addObject("project", project);
    mv.addObject("projectId", project.getExternalId());
    SUser user = SessionUser.getUser(request);
    Map<OAuthConnectionDescriptor, Boolean> oauthConnections = user == null || null == settings ?
                                                               null :
                                                               settings.getOAuthConnections(project, user);
    mv.addObject("oauthConnections", oauthConnections);
    mv.addObject("refreshTokenSupported", oauthConnections != null && oauthConnections.keySet().stream().anyMatch(c -> c.getOauthProvider().isTokenRefreshSupported()));

    if (settings != null) {
      settings.getSpecificAttributes(project, params).forEach(mv::addObject);
    }

    return mv;
  }

  private void transformParameters(@NotNull BasePropertiesBean props, @NotNull String publisherId, @NotNull ModelAndView mv) {
    CommitStatusPublisherSettings publisherSettings = myPublisherManager.findSettings(publisherId);
    if (publisherSettings == null)
      return;
    Map<String, String> transformed = publisherSettings.transformParameters(props.getProperties());
    if (transformed != null) {
      mv.addObject("propertiesBean", new BasePropertiesBean(transformed));
    }
  }

  @NotNull
  private ModelAndView createAddPublisherModel() {
    ModelAndView mv = new ModelAndView(myDescriptor.getPluginResourcesPath("addPublisher.jsp"));
    mv.addObject("publishers", getPublisherSettings(true));
    return mv;
  }

  @NotNull
  private ModelAndView createEditPublisherModel(@NotNull String publisherId) {
    ModelAndView mv = new ModelAndView(myDescriptor.getPluginResourcesPath("editPublisher.jsp"));
    mv.addObject("publishers", getPublisherSettings(false));
    CommitStatusPublisherSettings publisherSettings = myPublisherManager.findSettings(publisherId);
    if (publisherSettings != null) {
      mv.addObject("editedPublisherUrl", publisherSettings.getEditSettingsUrl());
      mv.addObject("testConnectionSupported", publisherSettings.isTestConnectionSupported());
    }
    return mv;
  }

  @NotNull
  private static List<VcsRoot> getVcsRoots(@NotNull HttpServletRequest request) {
    List<VcsRoot> roots = new ArrayList<VcsRoot>();
    BuildTypeForm buildTypeForm = (BuildTypeForm) request.getAttribute("buildForm");
    for (VcsRootEntry entry : buildTypeForm.getVcsRootsBean().getVcsRoots()) {
      roots.add(entry.getVcsRoot());
    }
    return roots;
  }

  @NotNull
  private static SProject getProject(@NotNull HttpServletRequest request) {
    BuildTypeForm buildTypeForm = (BuildTypeForm) request.getAttribute("buildForm");
    return buildTypeForm.getProject();
  }

  private List<CommitStatusPublisherSettings> getPublisherSettings(boolean newPublisher) {
    List<CommitStatusPublisherSettings> publishers = new ArrayList<CommitStatusPublisherSettings>(myPublisherManager.getAllPublisherSettings());
    if (newPublisher)
      publishers.add(0, new DummyPublisherSettings());
    return publishers;
  }
}
