package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.BaseJMockTestCase;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.systemProblems.*;
import jetbrains.buildServer.vcs.VcsManagerEx;
import org.jmock.Expectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 13/09/16.
 */

@Test
public class CommitStatusPublisherProblemsTest extends BaseJMockTestCase {

  private final static String FEATURE_1 = "PUBLISH_BUILD_FEATURE_1";
  private final static String FEATURE_2 = "PUBLISH_BUILD_FEATURE_2";

  private CommitStatusPublisherProblems myProblems;
  private SystemProblemNotificationEngine myProblemEngine;
  private SBuildType myBuildType;
  private VcsManagerEx myVcsManager;


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myVcsManager = m.mock(VcsManagerEx.class);
    myProblemEngine = new SystemProblemNotificationEngine();
    myProblemEngine.setVcsManager(myVcsManager);
    myProblems = new CommitStatusPublisherProblems(myProblemEngine);
    myBuildType = m.mock(SBuildType.class);
    m.checking(new Expectations() {{
      allowing(myBuildType).getBuildTypeId();
      will(returnValue("BT1"));
      allowing(myBuildType).getInternalId();
      will(returnValue("BT1_Internal"));
      allowing(myVcsManager).findVcsRoots(Collections.<Long>emptyList());
    }});
  }

  public void must_add_and_delete_problems() {
    myProblems.reportProblem(myBuildType, FEATURE_1, "Some problem description");
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myProblems.clearProblem(myBuildType, FEATURE_1);
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(0);
  }

  public void must_clear_obsolete_problems() {
    final String PUB1_P1 = "First issue of publisher 1";
    final String PUB2_P1 = "First issue of publisher 2";
    final String PUB2_P2 = "Second issue of publisher 2";

    myProblems.reportProblem(myBuildType, FEATURE_2, PUB2_P1);
    myProblems.reportProblem(myBuildType, FEATURE_1, PUB1_P1);
    myProblems.reportProblem(myBuildType, FEATURE_2, PUB2_P2);
    Collection<SystemProblemEntry> problems = myProblemEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(2);
    for (SystemProblemEntry pe: problems) {
      String description;
      description = pe.getProblem().getDescription();
      if(!description.equals(PUB1_P1))
        then(description).isEqualTo(PUB2_P2);
    }
    myProblems.clearObsoleteProblems(myBuildType, Collections.singletonList(FEATURE_1));
    Collection<SystemProblemEntry> remainingProblems = myProblemEngine.getProblems(myBuildType);
    then(remainingProblems.size()).isEqualTo(1);
    then(remainingProblems.iterator().next().getProblem().getDescription()).isEqualTo(PUB1_P1);
  }

}
