

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

package jetbrains.buildServer.commitPublisher.gitlab.data;

/**
 * This class does not represent full repository information.
 */
public class GitLabRepoInfo {
  public String id;
  public GitLabPermissions permissions;

  public GitLabRepoInfo(String id, GitLabPermissions permissions) {
    this.id = id;
    this.permissions = permissions;
  }

  @Override
  public int hashCode() {
    return id != null ?
      id.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o  == null || getClass() != o.getClass()) return false;
    GitLabRepoInfo repoInfo = (GitLabRepoInfo) o;

    return id != null && repoInfo.id != null && id.equals(repoInfo.id);
  }
}