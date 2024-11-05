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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import org.jetbrains.annotations.NotNull;

public class ResponseEntityProcessor<T> extends DefaultHttpResponseProcessor {
  private static final String JSON_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  private final Class<T> myType;
  private final Gson myGson;

  private T myResult;

  public ResponseEntityProcessor(Class<T> type) {
    myType = type;
    myGson = new GsonBuilder().setDateFormat(JSON_DATE_TIME_FORMAT).create();
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    boolean shouldContinueProcessing = handleError(response);
    if (!shouldContinueProcessing) {
      myResult = null;
      return;
    }
    final String content = response.getContent();
    if (content == null) {
      throw new HttpPublisherException("Unexpected empty content in reponse");
    }
    try {
      myResult = myGson.fromJson(content, myType);
    } catch (JsonSyntaxException e) {
      throw new HttpPublisherException("Invalid response: " + e.getMessage(), e);
    }
  }

  /**
   * Method defines how HTTP errors should be processed and what should be done with following processing in case of error
   * @return true if processing can be continued, otherwise - false
   * @throws HttpPublisherException in case, when processing should not be continued any way
   */
  protected boolean handleError(@NotNull HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    super.processResponse(response);
    return true;
  }

  public T getProcessingResult() {
    return myResult;
  }
}
