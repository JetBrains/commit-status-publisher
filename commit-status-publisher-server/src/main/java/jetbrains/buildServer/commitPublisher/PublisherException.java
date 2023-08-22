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

package jetbrains.buildServer.commitPublisher;
import org.jetbrains.annotations.NotNull;

public class PublisherException extends Exception {

  private boolean myShouldRetry = false;

  public PublisherException(@NotNull String message) {
    super(message);
  }

  public PublisherException(@NotNull String message, Throwable cause) {
    super(message, cause);
    if (cause instanceof PublisherException) {
      myShouldRetry = ((PublisherException)cause).myShouldRetry;
    }
  }

  /**
   * Indicates that we should retry publishing status in {@link CommitStatusPublisherListener}
   */
  public PublisherException setShouldRetry() {
    myShouldRetry = true;
    return this;
  }

  public boolean shouldRetry() {
    return myShouldRetry;
  }

}
