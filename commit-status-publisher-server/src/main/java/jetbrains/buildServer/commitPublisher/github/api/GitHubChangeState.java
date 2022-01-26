/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher.github.api;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
* Created by Eugene Petrenko (eugene.petrenko@gmail.com)
* Date: 06.09.12 0:13
*/
public enum GitHubChangeState {
  Pending("pending"),
  Success("success"),
  Error("error"),
  Failure("failure");


  private static final Map<String, GitHubChangeState> STATE_MAPPING = Arrays.stream(values()).collect(Collectors.toMap(GitHubChangeState::getState, Function.identity()));

  private final String myState;

  GitHubChangeState(@NotNull final String state) {
    myState = state;
  }

  @NotNull
  public String getState() {
    return myState;
  }

  public static GitHubChangeState getByState(String state) {
    return STATE_MAPPING.get(state);
  }
}
