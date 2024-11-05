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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.http.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoggerUtil {

  public static final String LOG_CATEGORY = "jetbrains.buildServer.COMMIT_STATUS";
  public static final Logger LOG = Logger.getInstance(LOG_CATEGORY);


  public static void logRequest(@NotNull String publisherId,
                                @NotNull HttpMethod method,
                                @NotNull String uri,
                                @Nullable String requestEntity) {
    if (!LOG.isDebugEnabled()) return;

    if (requestEntity == null) {
      requestEntity = "<none>";
    }

    LOG.debug("Calling " + publisherId + " with:\n" +
            "  requestURL: " + uri + "\n" +
            "  requestMethod: " + method + "\n" +
            "  requestEntity: " + requestEntity
    );
  }
}
