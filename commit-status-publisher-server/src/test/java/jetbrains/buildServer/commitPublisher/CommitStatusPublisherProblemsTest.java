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

import java.util.Collection;
import java.util.Collections;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.BuildFeatureDescriptorImpl;
import jetbrains.buildServer.serverSide.systemProblems.BuildFeatureProblemsTicketManager;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 13/09/16.
 */

@Test
public class CommitStatusPublisherProblemsTest extends BaseServerTestCase {

  private final static String FEATURE_1 = "PUBLISH_BUILD_FEATURE_1";
  private final static String FEATURE_2 = "PUBLISH_BUILD_FEATURE_2";

  private CommitStatusPublisherProblems myProblems;
  private SystemProblemNotificationEngine myProblemEngine;
  private CommitStatusPublisher myPublisher;
  private PublisherLogger myLogger;
  private CommitStatusPublisherSettings myPublisherSettings;
  private WebLinks myLinks;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new PublisherLogger();
    myProblemEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myFixture.getSingletonService(BuildFeatureProblemsTicketManager.class));
    myPublisherSettings = new MockPublisherSettings(myProblems);
    myLinks = myFixture.getSingletonService(WebLinks.class);
    myPublisher = new MockPublisher(myPublisherSettings, "PUBLISHER1", myBuildType, FEATURE_1, Collections.emptyMap(), myProblems, myLogger, myLinks);
  }

  public void must_add_and_delete_problems() {
    myProblems.reportProblem("Some problem description", myPublisher, "Build description", null, null, myLogger);
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(1);
    String lastLogged = myLogger.popLast();
    then(lastLogged)
      .contains("Some problem description")
      .contains(myPublisher.getId())
      .contains("Build description");
    myProblems.clearProblem(myPublisher);
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(0);
  }

  public void must_clear_obsolete_problems() {
    final String PUB1_P1 = "First issue of publisher 1";
    final String PUB2_P1 = "First issue of publisher 2";
    final String PUB2_P2 = "Second issue of publisher 2";
    CommitStatusPublisher publisher2 = new MockPublisher(myPublisherSettings, "PUBLISHER2", myBuildType, FEATURE_2,
                                                         Collections.emptyMap(), myProblems, myLogger, myLinks);

    myBuildType.addBuildFeature(new BuildFeatureDescriptorImpl(FEATURE_2, CommitStatusPublisherFeature.TYPE, Collections.emptyMap(), myServer));
    myBuildType.addBuildFeature(new BuildFeatureDescriptorImpl(FEATURE_1, CommitStatusPublisherFeature.TYPE, Collections.emptyMap(), myServer));
    myBuildType.persist();

    myProblems.reportProblem(PUB2_P1, publisher2, "Build description", null, null, myLogger);
    myProblems.reportProblem(PUB1_P1, myPublisher, "Build description", null, null, myLogger);
    myProblems.reportProblem(PUB2_P2, publisher2, "Build description", null, null, myLogger);
    Collection<SystemProblemEntry> problems = myProblemEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(3);
    myBuildType.removeBuildFeature(FEATURE_2);
    myProblems.clearObsoleteProblems(myBuildType);
    Collection<SystemProblemEntry> remainingProblems = myProblemEngine.getProblems(myBuildType);
    then(remainingProblems.size()).isEqualTo(1);
    then(remainingProblems.iterator().next().getProblem().getDescription()).contains(PUB1_P1);
  }

}
