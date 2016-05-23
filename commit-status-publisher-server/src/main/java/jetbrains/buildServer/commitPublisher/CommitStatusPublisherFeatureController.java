package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
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

  public CommitStatusPublisherFeatureController(@NotNull WebControllerManager controllerManager,
                                                @NotNull PluginDescriptor descriptor,
                                                @NotNull PublisherManager publisherManager,
                                                @NotNull PublisherSettingsController publisherSettingsController) {
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
    mv.addObject("vcsRoots", getVcsRoots(request));
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
    if (publisherSettings != null)
      mv.addObject("editedPublisherUrl", publisherSettings.getEditSettingsUrl());
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
