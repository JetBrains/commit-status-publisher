package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ServerListener {

  public ServerListener(@NotNull EventDispatcher<ConfigActionsServerListener> dispatcher) {
    dispatcher.addListener(new ConfigActionsServerAdapter() {
      @Override
      public void vcsRootExternalIdChanged(@NotNull ConfigAction cause, @NotNull SVcsRoot vcsRoot, @NotNull String oldExternalId, @NotNull String newExternalId) {
        super.vcsRootExternalIdChanged(cause, vcsRoot, oldExternalId, newExternalId);
        SProject vcsRootProject = vcsRoot.getProject();
        for (SBuildType bt: vcsRootProject.getBuildTypes()) {
          if (updatedFeatures(oldExternalId, newExternalId, bt)) {
            bt.persist(cause);
          }
        }

        for (BuildTypeTemplate tpl: vcsRootProject.getBuildTypeTemplates()) {
          if (updatedFeatures(oldExternalId, newExternalId, tpl)) {
            tpl.persist(cause);
          }
        }
      }
    });
  }

  private boolean updatedFeatures(@NotNull String oldExternalId, @NotNull String newExternalId, @NotNull BuildTypeSettings btSettings) {
    boolean updated = false;
    for (SBuildFeatureDescriptor bf: btSettings.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)) {
      if (oldExternalId.equals(bf.getParameters().get(Constants.VCS_ROOT_ID_PARAM))) {
        Map<String, String> params = new HashMap<String, String>(bf.getParameters());
        params.put(Constants.VCS_ROOT_ID_PARAM, newExternalId);
        btSettings.updateBuildFeature(bf.getId(), bf.getType(), params);
        updated = true;
      }
    }
    return updated;
  }
}
