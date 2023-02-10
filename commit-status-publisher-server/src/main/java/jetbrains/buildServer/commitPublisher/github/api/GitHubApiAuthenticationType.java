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

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum GitHubApiAuthenticationType {
  TOKEN_AUTH("token"),
  PASSWORD_AUTH("password"),

  STORED_TOKEN("storedToken");
  ;

  private final String myValue;


  GitHubApiAuthenticationType(@NotNull final String value) {
    myValue = value;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  @NotNull
  public static GitHubApiAuthenticationType parse(@Nullable final String value) {
    //migration
    if (value == null || StringUtil.isEmptyOrSpaces(value)) return PASSWORD_AUTH;

    for (GitHubApiAuthenticationType v : values()) {
      if (v.getValue().equals(value)) return v;
    }

    throw new IllegalArgumentException("Failed to parse GitHubApiAuthenticationType: " + value);
  }
}
