package jetbrains.buildServer.commitPublisher.gitea;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.pipeline.PipelineProject;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class GiteaBuildNameProviderTest extends BaseServerTestCase {

  private final GiteaBuildNameProvider myProvider = new GiteaBuildNameProvider();

  @Test
  public void get_name_for_head() {
    final String pipelineName = "Pipeline Project";
    PipelineProject pipelineProject = myProject.createPipelineProject("extId", pipelineName, "name: name");
    SBuildType pipelineHead = pipelineProject.getPipelineHead();
    SFinishedBuild build = createBuild(pipelineHead, Status.NORMAL);
    String buildName = myProvider.getBuildName(build.getBuildPromotion());

    then(buildName).isEqualTo(String.format("%s / %s", myProject.getName(), pipelineName));
  }
}
