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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public abstract class HttpBasedCommitStatusPublisher<Status> extends BaseCommitStatusPublisher implements HttpResponseProcessor<HttpPublisherException> {

  private final HttpResponseProcessor<HttpPublisherException> myHttpResponseProcessor;
  protected final WebLinks myLinks;

  public HttpBasedCommitStatusPublisher(@NotNull CommitStatusPublisherSettings settings,
                                        @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                                        @NotNull Map<String, String> params,
                                        @NotNull CommitStatusPublisherProblems problems, WebLinks links) {
    super(settings, buildType, buildFeatureId, params, problems);
    myLinks = links;
    myHttpResponseProcessor = new DefaultHttpResponseProcessor();
  }

  @Override
  protected WebLinks getLinks() {
    return myLinks;
  }

  protected void postJson(@NotNull final String url,
                          @Nullable final HttpCredentials credentials,
                          @Nullable final String data,
                          @Nullable final Map<String, String> headers,
                          @NotNull final String buildDescription) {
    try {
      LoggerUtil.logRequest(getId(), HttpMethod.POST, url, data);
      IOGuard.allowNetworkCall(() -> HttpHelper.post(url, credentials, data, ContentType.APPLICATION_JSON, headers, getConnectionTimeout(), getSettings().trustStore(), this));
    } catch (Exception ex) {
      myProblems.reportProblem("Commit Status Publisher HTTP request has failed", this, buildDescription, url, ex, LOG);
    }
  }

  @Nullable
  protected <T> T get(@NotNull final String url,
                     @Nullable final HttpCredentials credentials,
                     @Nullable final Map<String, String> headers,
                     @NotNull final ResponseEntityProcessor<T> responseProcessor) throws PublisherException {
    try {
      LoggerUtil.logRequest(getId(), HttpMethod.GET, url, null);
      IOGuard.allowNetworkCall(() -> HttpHelper.get(url, credentials, headers, getConnectionTimeout(), getSettings().trustStore(), responseProcessor));
      return responseProcessor.getProcessingResult();
    } catch (Exception ex) {
      throw new PublisherException("Commit Status Publisher HTTP request has failed", ex);
    }
  }

  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    myHttpResponseProcessor.processResponse(response);
  }

  protected static String encodeParameter(@NotNull String key, @NotNull String value) {
    try {
      return key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      LOG.warn(String.format("Failed to encode URL parameter \"%s\" value: \"%s\"", key, value), e);
      return key + "=" + value;
    }
  }

}
