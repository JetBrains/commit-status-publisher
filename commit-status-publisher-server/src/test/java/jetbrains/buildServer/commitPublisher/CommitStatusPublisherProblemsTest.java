package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.systemProblems.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

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

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new PublisherLogger();
    myProblemEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new CommitStatusPublisherProblems(myProblemEngine);
    myPublisherSettings = new MockPublisherSettings(myProblems);
    myPublisher = new MockPublisher(myPublisherSettings, "PUBLISHER1", myBuildType, FEATURE_1, Collections.<String, String>emptyMap(), myProblems);
  }

  public void must_add_and_delete_problems() {
    myProblems.reportProblem("Some problem description", myPublisher, "Build description", null, null, myLogger);
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(1);
    String lastLogged = myLogger.popLast();
    then(lastLogged.contains("Some problem description"));
    then(lastLogged.contains(myPublisher.getId()));
    then(lastLogged.contains("Build description"));
    myProblems.clearProblem(myPublisher);
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(0);
  }

  public void must_clear_obsolete_problems() {
    final String PUB1_P1 = "First issue of publisher 1";
    final String PUB2_P1 = "First issue of publisher 2";
    final String PUB2_P2 = "Second issue of publisher 2";
    CommitStatusPublisher publisher2 = new MockPublisher(myPublisherSettings, "PUBLISHER2", myBuildType, FEATURE_2, Collections.<String, String>emptyMap(), myProblems);

    myProblems.reportProblem(PUB2_P1, publisher2, "Build description", null, null, myLogger);
    myProblems.reportProblem(PUB1_P1, myPublisher, "Build description", null, null, myLogger);
    myProblems.reportProblem(PUB2_P2, publisher2, "Build description", null, null, myLogger);
    Collection<SystemProblemEntry> problems = myProblemEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(3);
    myProblems.clearObsoleteProblems(myBuildType, Collections.singletonList(FEATURE_1));
    Collection<SystemProblemEntry> remainingProblems = myProblemEngine.getProblems(myBuildType);
    then(remainingProblems.size()).isEqualTo(1);
    then(remainingProblems.iterator().next().getProblem().getDescription()).contains(PUB1_P1);
  }

}
