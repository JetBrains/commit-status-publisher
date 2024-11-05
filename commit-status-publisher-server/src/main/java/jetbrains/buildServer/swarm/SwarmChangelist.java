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
