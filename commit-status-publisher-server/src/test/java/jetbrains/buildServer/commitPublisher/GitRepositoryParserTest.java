package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ConstantConditions")
@Test
public class GitRepositoryParserTest {

  @TestFor(issues = {"TW-43075", "TW-45758", "TW-46969"})
  public void parse_scp_like_urls() {
    parse_scp_like_urls_ex("owner");
  }

  @TestFor(issues = {"TW-43075", "TW-45758", "TW-46969"})
  public void parse_scp_like_urls_numerical_owner() {
    parse_scp_like_urls_ex("777");
  }

  private void parse_scp_like_urls_ex(String owner) {
    List<String> urls = Arrays.asList(
            "git@github.com:%s/repository.git",
            "ssh://git@github.com:%s/repository.git",
            "ssh://git@bitbucket.org/%s/repository.git",
            "ssh://git@bitbucket.org/%s/repository",
            "ssh://git@altssh.bitbucket.org:443/%s/repository.git",
            "ssh://bitbucket.org/%s/repository");

    for(String url : urls) {
      String urlWithOwner = String.format(url, owner);
      Repository repo = GitRepositoryParser.parseRepository(urlWithOwner);
      then(repo).overridingErrorMessage("Failed to parse url " + urlWithOwner).isNotNull();
      then(repo.owner()).isEqualTo(owner);
      then(repo.repositoryName()).isEqualTo("repository");
    }
  }

  public void fails_to_parse_malformed_urls() {
    List<String> urls = Arrays.asList(
            "git@github.com/repository.git",
            "ssh://git@bitbucket.org:owner:777/repository.git",
            "ssh://git@bitbucket.org::owner/repository.git");

    for(String url : urls) {
      then(GitRepositoryParser.parseRepository(url)).isNull();
    }
  }

  @TestFor(issues = "TW-47493")
  public void parse_git_like_urls() {
    Repository repo = GitRepositoryParser.parseRepository("git://github.com/owner/repository.git");
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
  }


  @TestFor(issues = "TW-43075")
  public void parse_scp_like_urls_ghe() {
    Repository repo = GitRepositoryParser.parseRepository("git@ghe.server:owner/repository.git");
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
  }


  public void parse_http_urls() {
    List<String> urls = Arrays.asList(
            "https://owner@github.com/owner/repository.git",
            "https://github.com/owner/repository.git",
            "https://owner@bitbucket.org/owner/repository.git",
            "https://bitbucket.org/owner/repository.git");
    for(String url : urls) {
      Repository repo = GitRepositoryParser.parseRepository(url);
      then(repo.owner()).isEqualTo("owner");
      then(repo.repositoryName()).isEqualTo("repository");
    }
  }
}
