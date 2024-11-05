

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

package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

/**
* Created by Eugene Petrenko (eugene.petrenko@gmail.com)
* Date: 04.03.13 22:33
*/
@SuppressWarnings("UnusedDeclaration")
public class CommitStatus {
  @Nullable public final String state;
  @Nullable public final String target_url;
  @Nullable public final String description;
  @Nullable public final String context;

  public CommitStatus(@Nullable String state, @Nullable String target_url, @Nullable String description, @Nullable String context) {
    this.state = state;
    this.target_url = target_url;
    this.description = truncateStringValueWithDotsAtEnd(description, 140);
    this.context = context;
  }

  @Nullable
  private static String truncateStringValueWithDotsAtEnd(@Nullable final String str, final int maxLength) {
    if (str == null) return null;
    if (str.length() > maxLength) {
      return str.substring(0, maxLength - 2) + "\u2026";
    }
    return str;
  }
}