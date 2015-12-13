package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ConstantConditions")
@Test
public class GitRepositoryHelperTest {

  @TestFor(issues = "TW-43075")
  public void parse_scp_like_urls() {
    List<String> urls = Arrays.asList(
            "git@github.com:owner/repository.git",
            "ssh://git@github.com:owner/repository.git",
            "https://owner@bitbucket.org/owner/repository.git"
    );

    for(String url : urls)
    {
      GitRepository repo = GitRepositoryHelper.getRepositoryDetails(url);
      then(repo.owner()).isEqualTo("owner");
      then(repo.repositoryName()).isEqualTo("repository");
    }
  }


  @TestFor(issues = "TW-43075")
  public void parse_scp_like_urls_ghe() {
    GitRepository repo = GitRepositoryHelper.getRepositoryDetails("git@ghe.server:owner/repository.git");
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
  }

}
