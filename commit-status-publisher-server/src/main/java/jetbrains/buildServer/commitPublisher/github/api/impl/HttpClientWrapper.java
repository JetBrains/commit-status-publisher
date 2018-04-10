/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
