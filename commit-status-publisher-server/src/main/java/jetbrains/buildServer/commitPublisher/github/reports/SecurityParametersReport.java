/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.github.reports;

import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SecurityParametersReport extends HealthStatusReport {

  private static final String REPORT_TYPE = "githubPullRequestSecurityParams";
  private static final ItemCategory CATEGORY = new ItemCategory("githubPullRequestSecurityParamsCategory",
          "Security parameters used in configuration building pull requests", ItemSeverity.WARN);

  @NotNull
  @Override
  public String getType() {
    return REPORT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Security parameters used in configuration building pull requests";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope healthStatusScope) {
    return !healthStatusScope.globalItems();
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (!isEnabled())
      return;

    for (SBuildType bt : scope.getBuildTypes()) {
      if (bt.getProject().isArchived()) continue;
      if (!hasSecureParameters(bt))
        continue;
      List<VcsRootInstance> pullRequestRoots = new ArrayList<VcsRootInstance>();
      for (VcsRootInstance root : pullRequestVcsRoots(bt)) {
        if (githubRoot(root))
          pullRequestRoots.add(root);
      }
      if (!pullRequestRoots.isEmpty()) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("buildType", bt);
        data.put("roots", pullRequestRoots);
        consumer.consumeForBuildType(bt, new HealthStatusItem(REPORT_TYPE + bt.getExternalId(), CATEGORY, data));
      }
    }
  }


  private boolean hasSecureParameters(@NotNull SBuildType bt) {
    for (Parameter p : bt.getParametersCollection()) {
      ControlDescription description = p.getControlDescription();
      if (description == null)
        continue;
      if (Constants.PASSWORD_PARAMETER_TYPE.equals(description.getParameterType()))
        return true;
    }
    return false;
  }

  @NotNull
  private List<VcsRootInstance> pullRequestVcsRoots(@NotNull SBuildType bt) {
    return ((BuildTypeEx) bt).getLastUsedBranchSpecMap().entrySet().stream().filter(e -> e.getValue().asString().contains("+:refs/pull")).map(e -> e.getKey()).collect(Collectors.toList());
  }


  private boolean githubRoot(@NotNull VcsRootInstance root) {
    String url = root.getProperties().get(Constants.GIT_URL_PARAMETER);
    return url != null && url.contains("github.com");
  }


  @NotNull
  private List<VcsRootInstance> getGitRoots(@NotNull SBuildType bt) {
    List<SVcsRoot> gitRoots = getGitParentRoots(bt);
    List<VcsRootInstance> result = new ArrayList<VcsRootInstance>(gitRoots.size());
    for (SVcsRoot parentRoot : gitRoots) {
      VcsRootInstance root = bt.getVcsRootInstanceForParent(parentRoot);
      if (root != null)
        result.add(root);
    }
    return result;
  }


  @NotNull
  private List<SVcsRoot> getGitParentRoots(@NotNull SBuildType bt) {
    List<SVcsRoot> result = new ArrayList<SVcsRoot>();
    for (SVcsRoot parentRoot : bt.getVcsRoots()) {
      if (Constants.GIT_VCS_NAME.equals(parentRoot.getVcsName()))
        result.add(parentRoot);
    }
    return result;
  }


  private boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.github.securityParamsReportEnabled");
  }
}
