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

package jetbrains.buildServer.commitPublisher.bitbucketCloud.data;

import java.util.Collection;

public class BitbucketCloudBuildStatuses {
  public final Collection<BitbucketCloudCommitBuildStatus> values;
  /**
   * Current number of objects on the existing page
   */
  public final int pagelen;
  /**
   * Page number of the current results
   */
  public final int page;
  /**
   * Total number of objects in the response (on all pages)
   */
  public final Integer size;

  public BitbucketCloudBuildStatuses(Collection<BitbucketCloudCommitBuildStatus> values, int pagelen, int page, int size) {
    this.values = values;
    this.pagelen = pagelen;
    this.page = page;
    this.size = size;
  }
}
