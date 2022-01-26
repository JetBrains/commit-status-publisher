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

package jetbrains.buildServer.commitPublisher.space;

import jetbrains.buildServer.commitPublisher.HttpHelper;
import jetbrains.buildServer.commitPublisher.HttpPublisherException;
import jetbrains.buildServer.commitPublisher.HttpResponseProcessor;

public class ContentResponseProcessor implements HttpResponseProcessor {

  private String content;

  public String getContent() {
    return content;
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException {
    int statusCode = response.getStatusCode();
    String responseContent = response.getContent();

    if (statusCode >= 400) {
      throw new HttpPublisherException(statusCode, response.getStatusText(), "HTTP response error: " + (responseContent != null ? responseContent : "<empty>"));
    }

    content = responseContent;
  }
}
