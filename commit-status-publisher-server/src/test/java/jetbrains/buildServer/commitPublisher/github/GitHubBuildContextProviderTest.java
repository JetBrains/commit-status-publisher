package jetbrains.buildServer.commitPublisher.github;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.pipeline.PipelineProject;
import jetbrains.buildServer.pipeline.builders.PipelineYamlBuilder;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class GitHubBuildContextProviderTest extends BaseServerTestCase {

  private final GitHubBuildContextProvider myProvider = new GitHubBuildContextProvider();

  public void should_use_build_name_as_context() throws Exception {
    final String expectedContext = "My Default Test Build Type (My Default Test Project)";
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    String buildName = myProvider.getBuildName(queuedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildName);

    RunningBuildEx startedBuild = myFixture.flushQueueAndWait();

    buildName = myProvider.getBuildName(startedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildName);
  }

  public void should_use_context_from_parameters() throws Exception {
    final String expectedContext = "My custom context name";
    myBuildType.addBuildParameter(new SimpleParameter(Constants.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM, expectedContext));
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    String buildName = myProvider.getBuildName(queuedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildName);

    RunningBuildEx startedBuild = myFixture.flushQueueAndWait();
    buildName = myProvider.getBuildName(startedBuild.getBuildPromotion());
    assertEquals(expectedContext, buildName);
  }

  @TestFor(issues="TW-54352")
  public void default_context_must_not_contains_long_unicodes() throws Exception {
    char[] btNameCharCodes = { 0x41, 0x200d, 0x42b, 0x20, 0x3042, 0x231a, 0xd83e, 0xdd20, 0x39, 0xfe0f, 0x20e3, 0xd83d, 0x20, 0xdee9, 0xfe0f };
    myBuildType.setName(new String(btNameCharCodes));
    char[] prjNameCharCodes =  { 0x45, 0x263A,  0x09, 0xd841, 0xdd20 };
    myBuildType.getProject().setName(new String(prjNameCharCodes));
    SBuild build = createBuild(myBuildType, Status.NORMAL);
    String buildName = myProvider.getBuildName(build.getBuildPromotion());
    char[] expectedBTNameCharCodes = { 0x41, 0x42b, 0x20, 0x3042, 0x231a, 0x39};
    char[] expectedPrjNameCharCodes =  { 0x45, 0x263A };
    then(buildName).isEqualTo(new String(expectedBTNameCharCodes) + " (" + new String(expectedPrjNameCharCodes) + ")");
  }

  @Test
  public void default_context_for_pipeline_head() throws Exception {
    final String pipelineName = "Pipeline Project";
    PipelineProject pipelineProject = myProject.createPipelineProject("extId", pipelineName, PipelineYamlBuilder.BASIC_YAML);
    SBuildType pipelineHead = pipelineProject.getPipelineHead();
    SFinishedBuild build = createBuild(pipelineHead, Status.NORMAL);
    String buildName = myProvider.getBuildName(build.getBuildPromotion());
    then(buildName).isEqualTo(String.format("%s (%s)", pipelineName, myProject.getName()));
  }
}
