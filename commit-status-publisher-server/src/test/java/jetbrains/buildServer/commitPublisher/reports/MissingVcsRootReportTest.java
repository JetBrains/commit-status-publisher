package jetbrains.buildServer.commitPublisher.reports;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import jetbrains.buildServer.serverSide.healthStatus.reports.StubHealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class MissingVcsRootReportTest extends BaseServerTestCase {

  private MissingVcsRootsReport myReport;
  private ScopeBuilder myScopeBuilder;
  private StubHealthStatusItemConsumer myItems;
  private SVcsRoot myVcsRoot;

  @Override
  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();
    myReport = new MissingVcsRootsReport(myProjectManager);
    myScopeBuilder = new ScopeBuilder();
    myItems = new StubHealthStatusItemConsumer();
    myFixture.registerVcsSupport("jetbrains.git");
    myVcsRoot = myProject.createVcsRoot("jetbrains.git", "vcs1", "vcs1");
  }

  public void report_when_no_vcs_roots() {
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, new HashMap<String, String>());
    myReport.report(myScopeBuilder.addBuildType(myBuildType).build(), myItems);
    then(myItems.getConsumedItems().stream().map(hi -> hi.getAdditionalData().get("buildType")).collect(Collectors.toList()))
      .isEqualTo(Arrays.asList(myBuildType));
  }

  public void report_when_vcs_root_missing() {
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, new HashMap<String, String>() {{
      put(Constants.VCS_ROOT_ID_PARAM, "Unknown VCS root");
    }});
    myReport.report(myScopeBuilder.addBuildType(myBuildType).build(), myItems);
    then(myItems.getConsumedItems().stream().map(hi -> hi.getAdditionalData().get("buildType")).collect(Collectors.toList()))
      .isEqualTo(Arrays.asList(myBuildType));
  }

  public void report_when_vcs_root_detached() {
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, new HashMap<String, String>() {{
      put(Constants.VCS_ROOT_ID_PARAM, myVcsRoot.getExternalId());
    }});
    myReport.report(myScopeBuilder.addBuildType(myBuildType).build(), myItems);
    then(myItems.getConsumedItems().stream().map(hi -> hi.getAdditionalData().get("buildType")).collect(Collectors.toList()))
      .isEqualTo(Arrays.asList(myBuildType));
  }

  public void dont_report_when_vcs_root_attached() {
    myBuildType.addVcsRoot(myVcsRoot);
    myBuildType.addBuildFeature(CommitStatusPublisherFeature.TYPE, new HashMap<String, String>() {{
      put(Constants.VCS_ROOT_ID_PARAM, myVcsRoot.getExternalId());
    }});
    myReport.report(myScopeBuilder.addBuildType(myBuildType).build(), myItems);
    then(myItems.getConsumedItems()).isEmpty();
  }
}
