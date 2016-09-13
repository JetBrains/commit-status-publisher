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

  private CommitStatusPublisherProblems myProblems;
  private SystemProblemNotificationEngine myProblemEngine;
  private MockPublisher myPublisher;
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
    myPublisher = new MockPublisher("MockPublisher1");
  }

  public void must_add_and_delete_problems() {
    m.checking(new Expectations() {{
      allowing(myBuildType).getBuildTypeId();
      will(returnValue("BT1"));
      allowing(myVcsManager).findVcsRoots(Collections.<Long>emptyList());
    }});
    myProblems.reportProblem(myBuildType, myPublisher, "Some problem description");
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myProblems.clearProblem(myBuildType, myPublisher);
    then(myProblemEngine.getProblems(myBuildType).size()).isEqualTo(0);
  }

  public void must_clear_obsolete_problems() {
    m.checking(new Expectations() {{
      allowing(myBuildType).getBuildTypeId();
      will(returnValue("BT1"));
      allowing(myVcsManager).findVcsRoots(Collections.<Long>emptyList());
    }});

    final String PUB1_P1 = "First issue of publisher 1";
    final String PUB2_P1 = "First issue of publisher 2";
    final String PUB2_P2 = "Second issue of publisher 2";

    MockPublisher secondPublisher = new MockPublisher("MockPublisher2");
    myProblems.reportProblem(myBuildType, secondPublisher, PUB2_P1);
    myProblems.reportProblem(myBuildType, myPublisher, PUB1_P1);
    myProblems.reportProblem(myBuildType, secondPublisher, PUB2_P2);
    Collection<SystemProblemEntry> problems = myProblemEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(2);
    for (SystemProblemEntry pe: problems) {
      String description;
      description = pe.getProblem().getDescription();
      if(!description.equals(PUB1_P1))
        then(description).isEqualTo(PUB2_P2);
    }
    myProblems.clearObsoleteProblems(myBuildType, Collections.singletonList(myPublisher));
    Collection<SystemProblemEntry> remainingProblems = myProblemEngine.getProblems(myBuildType);
    then(remainingProblems.size()).isEqualTo(1);
    then(remainingProblems.iterator().next().getProblem().getDescription()).isEqualTo("First issue of publisher 1");
  }

}
