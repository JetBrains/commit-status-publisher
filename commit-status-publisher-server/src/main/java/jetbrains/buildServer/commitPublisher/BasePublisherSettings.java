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

import com.google.gson.Gson;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BasePublisherSettings implements CommitStatusPublisherSettings {

  private static final String PARAM_PUBLISH_BUILD_QUEUED_STATUS = "commitStatusPublisher.publishQueuedBuildStatus";

  protected final PluginDescriptor myDescriptor;
  protected final WebLinks myLinks;
  protected final CommitStatusPublisherProblems myProblems;
  private final SSLTrustStoreProvider myTrustStoreProvider;
  private final ConcurrentHashMap<String, TimestampedServerVersion> myServerVersions;
  protected final Gson myGson = new Gson();

  public BasePublisherSettings(@NotNull PluginDescriptor descriptor,
                               @NotNull WebLinks links,
                               @NotNull CommitStatusPublisherProblems problems,
                               @NotNull SSLTrustStoreProvider trustStoreProvider) {
    myDescriptor = descriptor;
    myLinks= links;
    myProblems = problems;
    myTrustStoreProvider = trustStoreProvider;
    myServerVersions = new ConcurrentHashMap<>();
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    return null;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    return null;
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull final Map<String, String> params) {
    return String.format("Post commit status to %s", getName());
  }

  @NotNull
  @Override
  public Map<OAuthConnectionDescriptor, Boolean> getOAuthConnections(final SProject project, final SUser user) {
    return Collections.emptyMap();
  }

  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return true;
  }

  @Override
  public boolean isEventSupported(Event event, final SBuildType buildType, final Map<String, String> params) {
    return getSupportedEvents(buildType, params).contains(event);
  }

  @Override
  public boolean isTestConnectionSupported() { return false; }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    throw new UnsupportedOperationException(String.format("Test connection functionality is not supported by %s publisher", getName()));
  }

  @Nullable
  @Override
  public KeyStore trustStore() {
    return myTrustStoreProvider.getTrustStore();
  }

  protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
    return Collections.emptySet();
  }

  protected boolean isBuildQueuedSupported(final SBuildType buildType, final Map<String, String> params) {
    if (buildType instanceof BuildTypeEx) {
      return ((BuildTypeEx) buildType).getBooleanInternalParameter(PARAM_PUBLISH_BUILD_QUEUED_STATUS);
    }
    throw new IllegalStateException("Unexpected build type implementation: can not determine if queued build statuses publishing is enabled");
  }

  @Override
  @Nullable
  public String getServerVersion(@NotNull String url) {
    TimestampedServerVersion version = myServerVersions.get(url);
    if (version != null && !version.isObsolete())
      return version.get();
    final String v;
    try {
       v = retrieveServerVersion(url);
    } catch (PublisherException ex) {
      if (version != null) {
        // if we failed to retrieve the information, just renew the timestamp of the old one for now
        myServerVersions.put(url, new TimestampedServerVersion(version.get()));
        return version.get();
      }
      return null;
    }
    if (v != null) {
      version = new TimestampedServerVersion(v);
      myServerVersions.put(url, version);
      return v;
    }
    return null;
  }

  @Nullable
  protected String retrieveServerVersion(@NotNull String url) throws PublisherException {
    return null;
  }

  private static class TimestampedServerVersion {
    final static long EXPIRATION_TIME_MS = TimeUnit.DAYS.toMillis(1);
    final private String myServerVersion;
    final private long myTimestamp;

    TimestampedServerVersion(@NotNull String version) {
      myServerVersion = version;
      myTimestamp = System.currentTimeMillis();
    }

    @NotNull
    String get() {
      return myServerVersion;
    }

    boolean isObsolete() {
      return System.currentTimeMillis() - myTimestamp > EXPIRATION_TIME_MS;
    }
  }
}
