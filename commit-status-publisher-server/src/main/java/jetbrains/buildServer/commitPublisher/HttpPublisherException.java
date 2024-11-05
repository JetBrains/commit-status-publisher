

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

import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 21/12/16.
 */
public class HttpPublisherException extends PublisherException {

  private final Integer myStatusCode;

  public HttpPublisherException(String message) {
    super(message);
    myStatusCode = null;
  }

  public HttpPublisherException(String message, Throwable t) {
    super(message, t);
    myStatusCode = null;
  }

  public HttpPublisherException(int statusCode, String reason) {
    this(statusCode, reason, null);
  }

  public HttpPublisherException(int statusCode, String reason, @Nullable String message) {
    super(String.format("%sresponse code: %d, reason: %s", null == message ? "" : message + ", ", statusCode, reason));
    myStatusCode = statusCode;
  }

  public Integer getStatusCode() {
    return myStatusCode;
  }
}