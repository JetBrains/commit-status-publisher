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

package jetbrains.buildServer.commitPublisher.github.api;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SupportedVcsRootAuthentificationType {
  REFRESHABLE_TOKEN_AUTH("ACCESS_TOKEN"),
  TOKEN_PASSWORD_AUTH("PASSWORD");
  private final String myValue;

  SupportedVcsRootAuthentificationType(@NotNull String value) {
    myValue = value;
  }

  public String getValue() {
    return myValue;
  }

  public static boolean contains(@Nullable String vcsAuthType) {
    return Arrays.stream(values()).map(v -> v.getValue()).anyMatch(v -> v.equals(vcsAuthType));
  }
}
