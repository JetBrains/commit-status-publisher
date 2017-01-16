package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
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
  protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
    BasePropertiesBean props = (BasePropertiesBean) request.getAttribute("propertiesBean");
    String publisherId = props.getProperties().get(Constants.PUBLISHER_ID_PARAM);
    ModelAndView mv = publisherId != null ? createEditPublisherModel(publisherId) : createAddPublisherModel();
    if (publisherId != null)
      transformParameters(props, publisherId, mv);
    mv.addObject("publisherSettingsUrl", myPublisherSettingsController.getUrl());
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
    mv.addObject("projectId", getProjectId(request));
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
  private List<VcsRoot> getVcsRoots(@NotNull HttpServletRequest request) {
    List<VcsRoot> roots = new ArrayList<VcsRoot>();
    BuildTypeForm buildTypeForm = (BuildTypeForm) request.getAttribute("buildForm");
    for (VcsRootEntry entry : buildTypeForm.getVcsRootsBean().getVcsRoots()) {
      roots.add(entry.getVcsRoot());
    }
    return roots;
  }

  @NotNull
  private String getProjectId(@NotNull HttpServletRequest request) {
    BuildTypeForm buildTypeForm = (BuildTypeForm) request.getAttribute("buildForm");
    return buildTypeForm.getProject().getExternalId();
  }

  private List<CommitStatusPublisherSettings> getPublisherSettings(boolean newPublisher) {
    List<CommitStatusPublisherSettings> publishers = new ArrayList<CommitStatusPublisherSettings>(myPublisherManager.getAllPublisherSettings());
    if (newPublisher)
      publishers.add(0, new DummyPublisherSettings());
    return publishers;
  }
}
