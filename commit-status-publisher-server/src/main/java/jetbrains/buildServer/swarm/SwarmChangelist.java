package jetbrains.buildServer.swarm;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class SwarmChangelist {
  private final SwarmClient mySwarmClient;
  private final long myChangelist;
  private final boolean isShelve;

  public SwarmChangelist(@NotNull SwarmClient swarmClient, long changelist, boolean isShelve) {
    mySwarmClient = swarmClient;
    myChangelist = changelist;
    this.isShelve = isShelve;
  }

  public SwarmClient getSwarmClient() {
    return mySwarmClient;
  }

  public long getChangelist() {
    return myChangelist;
  }

  public boolean isShelved() {
    return isShelve;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SwarmChangelist that = (SwarmChangelist)o;
    return myChangelist == that.myChangelist && mySwarmClient.equals(that.mySwarmClient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySwarmClient, myChangelist);
  }
}
