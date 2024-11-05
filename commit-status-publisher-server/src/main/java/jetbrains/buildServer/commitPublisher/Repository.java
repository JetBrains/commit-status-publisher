

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

import org.jetbrains.annotations.NotNull;

public class Repository {
  private final String myOwner, myRepo, myUrl;

  public Repository(@NotNull String url, @NotNull String owner, @NotNull String repo) {
    myUrl = url;
    myOwner = owner;
    myRepo = repo;
  }

  @NotNull
  public String url() {return myUrl; }

  @NotNull
  public String owner() {
    return myOwner;
  }

  @NotNull
  public String repositoryName() {
    return myRepo;
  }
}