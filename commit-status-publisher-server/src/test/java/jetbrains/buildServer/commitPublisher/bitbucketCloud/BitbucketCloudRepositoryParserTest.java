/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
