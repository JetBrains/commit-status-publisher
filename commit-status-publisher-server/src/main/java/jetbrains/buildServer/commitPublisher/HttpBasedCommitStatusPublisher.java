/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.ExceptionUtil;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class HttpBasedCommitStatusPublisher extends BaseCommitStatusPublisher implements HttpResponseProcessor {

  private final ExecutorServices myExecutorServices;
  private final HttpResponseProcessor myHttpResponseProcessor;

  public HttpBasedCommitStatusPublisher(@NotNull CommitStatusPublisherSettings settings,
                                        @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                                        @NotNull final ExecutorServices executorServices,
                                        @NotNull Map<String, String> params,
                                        @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
    myExecutorServices = executorServices;
    myHttpResponseProcessor = new DefaultHttpResponseProcessor();
  }

  protected void post(final String url, final String username, final String password,
                      final String data, final ContentType contentType, final Map<String, String> headers,
                      final String buildDescription) {
    try {
      IOGuard.allowNetworkCall(() -> HttpHelper.post(url, username, password, data, contentType, headers, getConnectionTimeout(), getSettings().trustStore(), this));
    } catch (Exception ex) {
      myProblems.reportProblem("Commit Status Publisher HTTP request has failed", this, buildDescription, url, ex, LOG);
    }
  }

  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    myHttpResponseProcessor.processResponse(response);
  }
}
