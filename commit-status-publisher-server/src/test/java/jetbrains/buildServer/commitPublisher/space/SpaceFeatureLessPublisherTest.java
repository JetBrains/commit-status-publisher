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

package jetbrains.buildServer.commitPublisher.space;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.oauth.ConnectionCapability;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthProvider;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.space.SpaceClientFactory;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConstants;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthKeys;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider;
import jetbrains.buildServer.serverSide.oauth.space.application.ApplicationInformationCapabilityResolver;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;

public class SpaceFeatureLessPublisherTest extends SpacePublisherTest {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    enableUnconditionalStatusPublishing();
    setupSpaceOAuthProvider();
    final OAuthConnectionDescriptor connection = addSpaceConnection();
    final OAuthToken token = createAccessToken(connection);
    addTokenToVcsRoot(connection, token);

    final CommitStatusPublisher featurelessPublisher = myPublisherSettings.createFeaturelessPublisher(myBuildType, myVcsRoot);
    if (featurelessPublisher == null) {
      fail("test setup is compromised, creating publisher must not fail");
    }

    myPublisher = featurelessPublisher;
  }

  // TW-87136: uconditional publishing must not report problems
  @Override
  public void should_report_timeout_failure() throws Exception {
    setPublisherTimeout(SHORT_TIMEOUT_TO_FAIL);
    myDoNotRespond = true;
    try {
      myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    } catch (PublisherException ignored) {}
    then(getRequestAsString()).isNull();
    then(myProblemNotificationEngine.getProblems(myBuildType)).isEmpty();
  }

  private void enableUnconditionalStatusPublishing() {
    myProject.addParameter(getParameterFactory().createSimpleParameter(SpaceConstants.FEATURE_TOGGLE_UNCONDITIONAL_COMMIT_STATUS, "true"));
  }

  private void setupSpaceOAuthProvider() {
    final ApplicationInformationCapabilityResolver mockCapabilityResolver = Mockito.mock(ApplicationInformationCapabilityResolver.class);
    Mockito.when(mockCapabilityResolver.apply(any(OAuthConnectionDescriptor.class), Mockito.eq(ConnectionCapability.PUBLISH_BUILD_STATUS)))
           .thenReturn(true);
    final SpaceClientFactory mockClientFactory = Mockito.mock(SpaceClientFactory.class);
    final SpaceOAuthProvider spaceOAuthProvider = new SpaceOAuthProvider(myWebLinks, mockClientFactory, mockCapabilityResolver);
    myServer.registerExtension(OAuthProvider.class, "test", spaceOAuthProvider);
  }

  @NotNull
  private OAuthConnectionDescriptor addSpaceConnection() {
    myProject.removeFeature(myProjectFeatureId);
    final OAuthConnectionDescriptor connection = myOAuthConnectionsManager.addConnection(myProject, SpaceOAuthProvider.TYPE, ImmutableMap.of(
      SpaceOAuthKeys.SPACE_CLIENT_ID, FAKE_CLIENT_ID,
      SpaceOAuthKeys.SPACE_CLIENT_SECRET, FAKE_CLIENT_SECRET,
      SpaceOAuthKeys.SPACE_SERVER_URL, getServerUrl()
    ));
    myProjectFeatureId = connection.getId();
    return connection;
  }

  @NotNull
  private OAuthToken createAccessToken(@NotNull OAuthConnectionDescriptor connection) {
    final OAuthToken token = new OAuthToken("access-token", "**", "x-oauth-user", 600, -1, new Date().getTime());
    return myOAuthTokenStorage.rememberToken(connection.getTokenStorageId(), token);
  }

  private void addTokenToVcsRoot(@NotNull OAuthConnectionDescriptor connection, @NotNull OAuthToken token) {
    final Map<String, String> newProperties = new HashMap<>(myVcsRoot.getProperties());
    newProperties.put("tokenId", connection.buildFullTokenId(token.getId()));
    myVcsRoot.setProperties(newProperties);
  }
}
