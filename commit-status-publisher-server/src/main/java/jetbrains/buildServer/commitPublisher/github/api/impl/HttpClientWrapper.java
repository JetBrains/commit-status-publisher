

package jetbrains.buildServer.commitPublisher.github.api.impl;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Consumer;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 12.08.11 15:13
 */
public interface HttpClientWrapper {

  void get(
    @NotNull String uri,
    @NotNull SimpleCredentials simpleCredentials,
    @NotNull Map<String, String> headers,
    @NotNull HTTPRequestBuilder.ResponseConsumer success,
    @NotNull HTTPRequestBuilder.ResponseConsumer error,
    @NotNull Consumer<Exception> exception
  ) throws IOException;

  void post(
    @NotNull String uri,
    @NotNull SimpleCredentials simpleCredentials,
    @NotNull Map<String, String> headers,
    @NotNull String data,
    @NotNull String mimeType,
    @NotNull Charset charset,
    @NotNull HTTPRequestBuilder.ResponseConsumer success,
    @NotNull HTTPRequestBuilder.ResponseConsumer error,
    @NotNull Consumer<Exception> exception
  ) throws IOException;
}