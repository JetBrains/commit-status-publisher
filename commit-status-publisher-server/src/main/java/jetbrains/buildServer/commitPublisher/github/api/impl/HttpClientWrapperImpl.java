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

package jetbrains.buildServer.commitPublisher.github.api.impl;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Consumer;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.util.http.RedirectStrategy;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 11.08.11 16:24
 */
public class HttpClientWrapperImpl implements HttpClientWrapper {

  private static final int RETRY_COUNT = 3;

  private final HTTPRequestBuilder.RequestHandler myRequestHandler;
  private final SSLTrustStoreProvider mySSLTrustStoreProvider;

  public HttpClientWrapperImpl(final HTTPRequestBuilder.RequestHandler requestHandler, final SSLTrustStoreProvider sslTrustStoreProvider) {
    myRequestHandler = requestHandler;
    mySSLTrustStoreProvider = sslTrustStoreProvider;
  }

  @Override
  public void get(
    @NotNull final String uri,
    @NotNull final SimpleCredentials simpleCredentials,
    @NotNull final Map<String, String> headers,
    @NotNull final HTTPRequestBuilder.ResponseConsumer success,
    @NotNull final HTTPRequestBuilder.ResponseConsumer error,
    @NotNull final Consumer<Exception> exception
  ) {
    try {
      final HTTPRequestBuilder.Request request =
        constructBuilder(uri, simpleCredentials, headers, success, error, exception)
          .withMethod(HttpMethod.GET)
          .build();
      myRequestHandler.doRequest(request);
    } catch (URISyntaxException e) {
      exception.accept(e);
    }
  }

  @Override
  public void post(
    @NotNull final String uri,
    @NotNull final SimpleCredentials simpleCredentials,
    @NotNull final Map<String, String> headers,
    @NotNull final String data,
    @NotNull final String mimeType,
    @NotNull final Charset charset,
    @NotNull final HTTPRequestBuilder.ResponseConsumer success,
    @NotNull final HTTPRequestBuilder.ResponseConsumer error,
    @NotNull final Consumer<Exception> exception
  ) {
    try {
      final HTTPRequestBuilder.Request request =
        constructBuilder(uri, simpleCredentials, headers, success, error, exception)
          .withMethod(HttpMethod.POST)
          .withPostStringEntity(data, mimeType, charset)
          .build();
      myRequestHandler.doRequest(request);
    } catch (URISyntaxException e) {
      exception.accept(e);
    }
  }

  private HTTPRequestBuilder constructBuilder(
    @NotNull final String uri,
    @NotNull final SimpleCredentials simpleCredentials,
    @NotNull final Map<String, String> headers,
    @NotNull final HTTPRequestBuilder.ResponseConsumer success,
    @NotNull final HTTPRequestBuilder.ResponseConsumer error,
    @NotNull final Consumer<Exception> exception
  ) throws URISyntaxException {
    return new HTTPRequestBuilder(uri)
      .withTimeout(TeamCityProperties.getInteger("teamcity.github.http.timeout", 10 * 1000))
      .withAuthenticateHeader(simpleCredentials)
      .withRedirectStrategy(RedirectStrategy.LAX)
      .withTrustStore(mySSLTrustStoreProvider.getTrustStore())
      .allowNonSecureConnection(true)
      .withEncodingInterceptor(true)
      .withRetryCount(RETRY_COUNT)
      .withHeader(headers)
      .onException(exception)
      .onErrorResponse(error)
      .onSuccess(success);
  }
}
