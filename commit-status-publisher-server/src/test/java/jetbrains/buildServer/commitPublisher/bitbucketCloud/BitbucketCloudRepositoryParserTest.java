

package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.util.Collections;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class BitbucketCloudRepositoryParserTest extends BaseServerTestCase {

  private BitbucketCloudRepositoryParser myParser;

  @Override
  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();
    myParser = new BitbucketCloudRepositoryParser();
  }

  public void parse_mercurial_root() {
    final String url = "ssh://hg@bitbucket.org/owner/repo";
    myFixture.registerVcsSupport("mercurial");
    SVcsRoot vcsRoot = myProject.createVcsRoot("mercurial", "myvcs1",
                                               Collections.singletonMap("repositoryPath", url));
    Repository repo = myParser.parseRepository(vcsRoot);
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repo");
    then(repo.url()).isEqualTo(url);
  }

  public void parse_git_root_lowercase_values() {
    final String url = "https://myusername@bitbucket.org/OwNeR/Repo-NAME.git";
    myFixture.registerVcsSupport("jetbrains.git");
    SVcsRoot vcsRoot = myProject.createVcsRoot("jetbrains.git", "myvcs1",
                                               Collections.singletonMap("url", url));
    Repository repo = myParser.parseRepository(vcsRoot);
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repo-name");
    then(repo.url()).isEqualTo(url);
  }
}