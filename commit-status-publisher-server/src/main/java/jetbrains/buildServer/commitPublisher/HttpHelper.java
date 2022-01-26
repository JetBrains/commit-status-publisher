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
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.util.http.RedirectStrategy;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 23/11/16.
 */
public class HttpHelper {
  private static final HTTPRequestBuilder.RequestHandler REQUEST_HANDLER =
    new HTTPRequestBuilder.ApacheClient43RequestHandler();

  private static void call(@NotNull HttpMethod method,
                           @NotNull String url,
                           @Nullable String username,
                           @Nullable String password,
                           @Nullable final Map<String, String> headers,
                           int timeout,
                           @Nullable final KeyStore trustStore,
                           @Nullable HttpResponseProcessor processor,
                           @Nullable Consumer<HTTPRequestBuilder> modifier
  ) throws IOException, HttpPublisherException {
    final AtomicReference<Exception> ex = new AtomicReference<Exception>();
    final AtomicReference<String> content = new AtomicReference<String>();
    final AtomicReference<Integer> code = new AtomicReference<Integer>(0);
    final AtomicReference<String> text = new AtomicReference<String>();

    final HTTPRequestBuilder builder;
    try {
      builder = new HTTPRequestBuilder(url);
      builder
        .withMethod(method)
        .withTimeout(timeout)
        .withCredentials(getCredentials(username, password))
        .withRedirectStrategy(RedirectStrategy.LAX)
        .withTrustStore(trustStore)
        .allowNonSecureConnection(true)
        .withPreemptiveAuthentication(true)
        .withHeader(headers)
        .onException(new Consumer<Exception>() {
          @Override
          public void accept(final Exception e) {
            ex.set(e);
          }
        })
        .onErrorResponse(new HTTPRequestBuilder.ResponseConsumer() {
          @Override
          public void consume(@NotNull final HTTPRequestBuilder.Response response) throws IOException {
            content.set(response.getBodyAsString());
            code.set(response.getStatusCode());
            text.set(response.getStatusText());
          }
        })
        .onSuccess(new HTTPRequestBuilder.ResponseConsumer() {
          @Override
          public void consume(@NotNull final HTTPRequestBuilder.Response response) throws IOException {
            content.set(response.getBodyAsString());
            code.set(response.getStatusCode());
            text.set(response.getStatusText());
          }
        });
    } catch (URISyntaxException e) {
      throw new IOException(String.format("Malformed URL '%s'", url), e);
    }
    if (modifier != null) {
      modifier.accept(builder);
    }

    REQUEST_HANDLER.doRequest(builder.build());
    Exception exception = ex.get();
    if (exception != null) {
      if (exception instanceof IOException) {
        throw (IOException)exception;
      } else {
        throw new RuntimeException(exception);
      }
    }

    if (processor != null) {
      processor.processResponse(new HttpResponse(code.get(), text.get(), content.get()));
    }
  }

  public static void post(@NotNull String url, @Nullable String username, @Nullable String password,
                          @Nullable final String data, @Nullable final ContentType contentType,
                          @Nullable final Map<String, String> headers, int timeout, @Nullable final KeyStore trustStore,
                          @Nullable HttpResponseProcessor processor) throws IOException, HttpPublisherException {
    http(HttpMethod.POST, url, username, password, data, contentType, headers, timeout, trustStore, processor);
  }

  public static void http(@NotNull HttpMethod httpMethod,
                          @NotNull String url,
                          @Nullable String username,
                          @Nullable String password,
                          @Nullable final String data,
                          @Nullable final ContentType contentType,
                          @Nullable final Map<String, String> headers,
                          int timeout,
                          @Nullable final KeyStore trustStore,
                          @Nullable HttpResponseProcessor processor) throws IOException, HttpPublisherException {

    call(httpMethod, url, username, password, headers, timeout, trustStore, processor, new Consumer<HTTPRequestBuilder>() {
      @Override
      public void accept(final HTTPRequestBuilder builder) {
        if (data != null && contentType != null) {
          builder.withPostStringEntity(data, contentType.getMimeType(), contentType.getCharset());
        }
      }
    });
  }

  public static void get(@NotNull String url, @Nullable String username, @Nullable String password,
                         @Nullable final Map<String, String> headers, int timeout, @Nullable final KeyStore trustStore,
                         @Nullable HttpResponseProcessor processor) throws IOException, HttpPublisherException {

    call(HttpMethod.GET, url, username, password, headers, timeout, trustStore, processor, null);
  }

  public static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }


  @Nullable
  private static SimpleCredentials getCredentials(@Nullable final String username, @Nullable final String password) {
    return username == null || password == null ? null : new SimpleCredentials(username, password);
  }

  public static class HttpResponse {

    private final int myStatusCode;
    private final String myStatusText;
    private final String myContent;

    public HttpResponse(final int statusCode, final String statusText, final String content) {
      myStatusCode = statusCode;
      myStatusText = statusText;
      myContent = content;
    }

    public int getStatusCode() {
      return myStatusCode;
    }

    public String getStatusText() {
      return myStatusText;
    }

    public String getContent() {
      return myContent;
    }
  }
}
