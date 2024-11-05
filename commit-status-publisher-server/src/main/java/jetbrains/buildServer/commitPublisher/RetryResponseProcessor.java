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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.stream.Collectors;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import org.jetbrains.annotations.NotNull;

public class RetryResponseProcessor implements HttpResponseProcessor<HttpPublisherException> {

  final static String RETRY_STATUS_CODES_PROPERTY_NAME = "teamcity.commitStatusPublisher.retry.statusCodes";
  @NotNull
  private static final HashSet<Integer> ourRetryableResponseCodes = new HashSet<>(Arrays.asList(
    408, // Request Timeout
    425, // Too Early
    429, // Too Many Requests
    500, // Internal Server Error
    502, // Bad Gateway
    503, // Service Unavailable
    504  // Gateway Timeout
  ));
  @NotNull
  private final HttpResponseProcessor<HttpPublisherException> myDelegate;

  public static boolean shouldRetryOnCode(int statusCode) {
    String statusCodesString = TeamCityProperties.getPropertyOrNull(RETRY_STATUS_CODES_PROPERTY_NAME);

    HashSet<Integer> statusCodes;
    if (statusCodesString == null) {
      statusCodes = ourRetryableResponseCodes;
    } else {
      statusCodes = new HashSet<>(Arrays.stream(statusCodesString.split(",")).map((strCode) -> Integer.parseInt(strCode)).collect(Collectors.toUnmodifiableList()));
    }
    return statusCodes.contains(statusCode);
  }

  public static void processNetworkException(@NotNull Throwable cause, @NotNull PublisherException ex) {
    if (cause instanceof IOException) {
      ex.setShouldRetry();
    }
  }

  public RetryResponseProcessor(@NotNull HttpResponseProcessor<HttpPublisherException> httpResponseProcessor) {
    myDelegate = httpResponseProcessor;
  }

  @Override
  public void processResponse(HttpHelper.HttpResponse response) throws IOException, HttpPublisherException {
    try {
      myDelegate.processResponse(response);
    } catch (PublisherException ex) {
      if (shouldRetryOnCode(response.getStatusCode())) {
        ex.setShouldRetry();
      }
      throw ex;
    }
  }

  public HttpResponseProcessor<HttpPublisherException> getProcessor() {
    return myDelegate;
  }
}
