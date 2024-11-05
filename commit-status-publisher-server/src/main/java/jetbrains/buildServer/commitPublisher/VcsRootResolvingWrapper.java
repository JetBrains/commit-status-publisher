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

import java.util.Map;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsRootResolvingWrapper implements VcsRoot {

  private final ValueResolver myValueResolver;
  private final VcsRoot myVcsRoot;

  public VcsRootResolvingWrapper(ValueResolver valueResolver, VcsRoot vcsRoot) {
    myValueResolver = valueResolver;
    myVcsRoot = vcsRoot;
  }

  @NotNull
  @Override
  public String describe(boolean verbose) {
    return myVcsRoot.describe(verbose);
  }

  @Override
  public long getId() {
    return myVcsRoot.getId();
  }

  @NotNull
  @Override
  public String getName() {
    return myVcsRoot.getName();
  }

  @NotNull
  @Override
  public String getVcsName() {
    return myVcsRoot.getVcsName();
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() {
    return myValueResolver.resolve(myVcsRoot.getProperties());
  }

  @Nullable
  @Override
  public String getProperty(@NotNull String propertyName) {
    String unresolvedValue = myVcsRoot.getProperty(propertyName);
    if (unresolvedValue == null) {
      return unresolvedValue;
    }
    return myValueResolver.resolve(unresolvedValue).getResult();
  }

  @Nullable
  @Override
  public String getProperty(@NotNull String propertyName, @Nullable String defaultValue) {
    String unresolvedValue = myVcsRoot.getProperty(propertyName);
    if (unresolvedValue == null) {
      return defaultValue;
    }
    return myValueResolver.resolve(unresolvedValue).getResult();
  }

  @NotNull
  @Override
  public String getExternalId() {
    return myVcsRoot.getExternalId();
  }
}
