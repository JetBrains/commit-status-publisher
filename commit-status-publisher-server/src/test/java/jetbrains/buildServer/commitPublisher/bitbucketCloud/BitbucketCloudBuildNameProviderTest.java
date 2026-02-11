package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.util.Collections;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.pipeline.PipelineProject;
import jetbrains.buildServer.pipeline.builders.PipelineYamlBuilder;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class BitbucketCloudBuildNameProviderTest extends BaseServerTestCase {
  private final BitbucketCloudBuildNameProvider myProvider = new BitbucketCloudBuildNameProvider();

  @Test
  public void get_name_for_head() {
    final String pipelineName = "Pipeline Project";
    PipelineProject pipelineProject = myProject.createPipelineProject("extId", pipelineName, PipelineYamlBuilder.BASIC_YAML);
    SBuildType pipelineHead = pipelineProject.getPipelineHead();
    SFinishedBuild build = createBuild(pipelineHead, Status.NORMAL);
    String buildName = myProvider.getBuildName(build.getBuildPromotion(), Collections.emptyMap());

    then(buildName).isEqualTo(String.format("%s / %s", myProject.getName(), pipelineName));
  }

  @Test
  public void get_build_name_from_parameters_test() {
    setInternalProperty("teamcity.commitStatusPublisher.buildName.customization.enable", true);
    final String expectedContext = "My custom context name";
    SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    String buildName = myProvider.getBuildName(queuedBuild.getBuildPromotion(), Collections.singletonMap("build_custom_name", "My custom context name"));
    assertEquals(expectedContext, buildName);

    RunningBuildEx startedBuild = myFixture.flushQueueAndWait();
    buildName = myProvider.getBuildName(startedBuild.getBuildPromotion(), Collections.singletonMap("build_custom_name", "My custom context name"));
    assertEquals(expectedContext, buildName);
  }
}
