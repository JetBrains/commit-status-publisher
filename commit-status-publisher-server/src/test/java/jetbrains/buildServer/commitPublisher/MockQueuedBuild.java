package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.AgentRestrictor;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockQueuedBuild implements SQueuedBuild {
  private String myItemId;
  private String myBuildTypeId;

  @NotNull
  @Override
  public Date getWhenQueued() {
    return null;
  }

  @NotNull
  @Override
  public String getItemId() {
    return myItemId;
  }

  public void setItemId(String itemId) {
    myItemId = itemId;
  }

  @NotNull
  @Override
  public String getBuildTypeId() {
    return myBuildTypeId;
  }

  public void setBuildTypeId(String buildTypeId) {
    myBuildTypeId = buildTypeId;
  }

  @Nullable
  @Override
  public Integer getBuildAgentId() {
    return null;
  }

  @Nullable
  @Override
  public AgentRestrictor getAgentRestrictor() {
    return null;
  }

  @Override
  public boolean isPersonal() {
    return false;
  }

  @Override
  public int getOrderNumber() {
    return 0;
  }

  @NotNull
  @Override
  public BuildPromotion getBuildPromotion() {
    return null;
  }

  @Nullable
  @Override
  public SBuild getSequenceBuild() {
    return null;
  }

  @NotNull
  @Override
  public Collection<SBuildAgent> getCompatibleAgents() {
    return null;
  }

  @NotNull
  @Override
  public CompatibilityResult getAgentCompatibility(@NotNull AgentDescription agentDescription) {
    return null;
  }

  @NotNull
  @Override
  public SBuildType getBuildType() throws BuildTypeNotFoundException {
    return null;
  }

  @NotNull
  @Override
  public List<SBuildAgent> getCanRunOnAgents() {
    return null;
  }

  @NotNull
  @Override
  public Map<SBuildAgent, CompatibilityResult> getCompatibilityMap() {
    return null;
  }

  @Nullable
  @Override
  public SBuildAgent getBuildAgent() {
    return null;
  }

  @Nullable
  @Override
  public BuildEstimates getBuildEstimates() {
    return null;
  }

  @Override
  public void removeFromQueue(@Nullable User user, String comment) {

  }

  @NotNull
  @Override
  public String getRequestor() {
    return null;
  }

  @Override
  public TriggeredBy getTriggeredBy() {
    return null;
  }
}
