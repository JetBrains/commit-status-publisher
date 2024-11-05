

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

package jetbrains.buildServer.commitPublisher.stash;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum StashBuildStatus {
  SUCCESSFUL, FAILED, INPROGRESS;

  private static final Map<String, StashBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(StashBuildStatus::name, Function.identity()));

  @Nullable
  public static StashBuildStatus getByName(@NotNull String name) {
    return INDEX.get(name);
  }
}