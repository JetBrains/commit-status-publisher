package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ServerListener  extends ConfigActionsServerAdapter implements CustomSettingsMapper  {

  public ServerListener(@NotNull EventDispatcher<ConfigActionsServerListener> dispatcher) {
    dispatcher.addListener(this);
  }

  @Override
  public void vcsRootExternalIdChanged(@NotNull ConfigAction cause, @NotNull SVcsRoot vcsRoot, @NotNull String oldExternalId, @NotNull String newExternalId) {
    super.vcsRootExternalIdChanged(cause, vcsRoot, oldExternalId, newExternalId);
    SProject vcsRootProject = vcsRoot.getProject();
    for (SBuildType bt: vcsRootProject.getBuildTypes()) {
      if (updateFeatures(oldExternalId, null, newExternalId, bt)) {
        bt.persist(cause);
      }
    }

    for (BuildTypeTemplate tpl: vcsRootProject.getBuildTypeTemplates()) {
      if (updateFeatures(oldExternalId, null, newExternalId, tpl)) {
        tpl.persist(cause);
      }
    }
  }

  @Override
  public void mapData(@NotNull CopiedObjects copiedObjects) {
    Map<SVcsRoot, SVcsRoot> mappedRoots = copiedObjects.getCopiedVcsRootsMap();
    if (mappedRoots.isEmpty()) return;

    for (BuildTypeSettings bt: copiedObjects.getCopiedSettingsMap().values()) {
      for (Map.Entry<SVcsRoot, SVcsRoot> e: mappedRoots.entrySet()) {
        updateFeatures(e.getKey().getExternalId(), e.getKey().getId(), e.getValue().getExternalId(), bt);
      }
    }
  }

  private static boolean updateFeatures(@NotNull String oldExternalId, @Nullable Long oldInternalId, @NotNull String newExternalId, @NotNull BuildTypeSettings btSettings) {
    boolean updated = false;
    for (SBuildFeatureDescriptor bf: btSettings.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)) {
      String vcsRootId = bf.getParameters().get(Constants.VCS_ROOT_ID_PARAM);
      Long internalId;
      try {
        internalId = Long.valueOf(vcsRootId);
      } catch (NumberFormatException ex) {
        internalId = null;
      }
      if (oldExternalId.equals(vcsRootId) || (null != oldInternalId && oldInternalId.equals(internalId))) {
        Map<String, String> params = new HashMap<String, String>(bf.getParameters());
        params.put(Constants.VCS_ROOT_ID_PARAM, newExternalId);
        btSettings.updateBuildFeature(bf.getId(), bf.getType(), params);
        updated = true;
      }
    }
    return updated;
  }
}
