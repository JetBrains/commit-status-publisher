package jetbrains.buildServer.commitPublisher.github;

import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ConstantConditions")
@Test
public class ChangeStatusUpdaterTest {

  @TestFor(issues = "TW-43075")
  public void parse_scp_like_urls() {
    ChangeStatusUpdater.GitHubRepo repo = ChangeStatusUpdater.getGitHubRepo("git@github.com:owner/repository.git");
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
  }


  @TestFor(issues = "TW-43075")
  public void parse_scp_like_urls_ghe() {
    ChangeStatusUpdater.GitHubRepo repo = ChangeStatusUpdater.getGitHubRepo("git@ghe.server:owner/repository.git");
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
  }
}
