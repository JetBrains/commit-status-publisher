

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ConstantConditions")
@Test
public class GitRepositoryParserTest {

  private GitRepositoryParser myGitRepositoryParser;
  
  @BeforeMethod
  protected void setUp() {
    myGitRepositoryParser = new GitRepositoryParser();
  }
  
  @TestFor(issues = {"TW-43075", "TW-45758", "TW-46969"})
  public void parse_ssh_urls() {
    parse_ssh_urls_ex("owner", null);
  }

  @TestFor(issues = {"TW-43075", "TW-45758", "TW-46969"})
  public void parse_ssh_urls_numerical_owner() {
    parse_ssh_urls_ex("777", null);
  }

  @TestFor(issues = "TW-49264")
  public void parse_ssh_urls_with_slashes() {
    parse_ssh_urls_ex("one/two/three", "");
  }

  @TestFor(issues = "TW-49264")
  public void parse_ssh_urls_with_slashes_and_path() {
    parse_ssh_urls_ex("one/two/three", "somepath/morepath/");
  }

  private void parse_ssh_urls_ex(String owner, String vcsRootPath) {
    List<String> urls = Arrays.asList(
            "git@gitlab.com:%s%s/repository.git",
            "git@github.com:/%s%s/repository.git",
            "git@github.com:/%s%s/repository.git/",
            "non_standard_name@github.com:%s%s/repository.git",
            "git.mygithubserver.com:%s%s/repository.git/",
            "ssh://git@github.com:%s%s/repository.git",
            "ssh://git@github.com:%s%s/repository.git/",
            "ssh://non_standard_name@github.com:%s%s/repository.git",
            "ssh://git@bitbucket.org/%s%s/repository.git",
            "ssh://git@bitbucket.org/%s%s/repository",
            "ssh://git@altssh.bitbucket.org:443/%s%s/repository.git",
            "ssh://bitbucket.org/%s%s/repository",
            "ssh://bitbucket.org/%s%s/repository/");

    for(String url : urls) {
      String urlWithOwner = String.format(url, null == vcsRootPath ? "" : vcsRootPath, owner);
      Repository repo = null == vcsRootPath ? myGitRepositoryParser.parseRepositoryUrl(urlWithOwner)
                                            : myGitRepositoryParser.parseRepositoryUrl(urlWithOwner, vcsRootPath);
      then(repo).overridingErrorMessage("Failed to parse url " + urlWithOwner).isNotNull();
      then(repo.owner()).as("Must parse owner from URL " + urlWithOwner).isEqualTo(owner);
      then(repo.repositoryName()).isEqualTo("repository");
      then(repo.url()).isEqualTo(urlWithOwner);
    }
  }

  public void fails_to_parse_malformed_urls() {
    List<String> urls = Arrays.asList(
            "https://url.com",
            "https://url.com/owner",
            "git@github.com/repository.git",
            "nothing/repository",
            "ssh://git@bitbucket.org:owner:777/repository.git",
            "ssh://git@bitbucket.org::owner/repository.git");

    for(String url : urls) {
      then(myGitRepositoryParser.parseRepositoryUrl(url)).isNull();
    }
  }

  @TestFor(issues = "TW-47493")
  public void parse_git_like_urls() {
    final String url = "git://github.com/owner/repository.git";
    Repository repo = myGitRepositoryParser.parseRepositoryUrl(url);
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
    then(repo.url()).isEqualTo(url);
  }


  @TestFor(issues = "TW-43075")
  public void parse_scp_like_urls_ghe() {
    final String url = "git@ghe.server:owner/repository.git";
    Repository repo = myGitRepositoryParser.parseRepositoryUrl(url);
    then(repo.owner()).isEqualTo("owner");
    then(repo.repositoryName()).isEqualTo("repository");
    then(repo.url()).isEqualTo(url);
  }

  public void parse_http_urls_with_slashes() {
    Map<String, String> urls = new HashMap<String, String>() {{
      put("HTTP://mygitlab.mydomain.com:8080/%s/repository.git", "");
      put("http://mygitlab.mydomain.com:8080/%s/repository.git", "");
      put("http://user@mygitlab.mydomain.com:8080/%s/repository.git", "");
      put("https://gitlab.com/%s/repository.git", "");
      put("https://mygitlab.mydomain.com:8080/%s/repository.git", "");
      put("https://mygitlab.mydomain.com/somepath/morepath/%s/repository.git", "/somepath/morepath");
      put("https://mygitlab.mydomain.com:8080/somepath/morepath/%s/repository.git", "/somepath/morepath");
      put("https://mygitlab.mydomain.com/somepath/morepath/%s/repository.git", "somepath/morepath");
      put("https://mygitlab.mydomain.com/somepath/morepath/%s/repository.git", "somepath/morepath/");
      put("https://mygitlab.mydomain.com/somepath/morepath/%s/repository.git", "/somepath/morepath/");
    }};
    for(Map.Entry<String, String> urlEntry : urls.entrySet()) {
      String url = urlEntry.getKey();
      String prefix = urlEntry.getValue();
      String urlWithOwner = String.format(url, "group/subgroup/owner");
      Repository repo = myGitRepositoryParser.parseRepositoryUrl(urlWithOwner, prefix);
      then(repo.owner()).as(String.format("Must parse owner in URL %s", urlWithOwner)).isEqualTo("group/subgroup/owner");
      then(repo.repositoryName()).isEqualTo("repository");
      then(repo.url()).isEqualTo(urlWithOwner);
    }
  }
  
  public void parse_http_urls() {
    List<String> urls = Arrays.asList(
            "HTTPS://owner@github.com/%s/repository.git",
            "https://owner@github.com/%s/repository.git",
            "https://owner@github.com/subdir/%s/repository.git",
            "https://github.com/%s/repository.git",
            "http://server.mygithub.com/%s/repository.git",
            "https://github.com/%s/repository",
            "https://github.com/%s/repository/",
            "https://git.myserver.com:8080/%s/repository",
            "https://owner@bitbucket.org/%s/repository.git",
            "https://bitbucket.org/%s/repository.git");
    for(String url : urls) {
      String urlWithOwner = String.format(url, "owner");
      Repository repo = myGitRepositoryParser.parseRepositoryUrl(urlWithOwner);
      then(repo.owner()).as(String.format("Must parse owner in URL %s", urlWithOwner)).isEqualTo("owner");
      then(repo.repositoryName()).isEqualTo("repository");
      then(repo.url()).isEqualTo(urlWithOwner);
    }
  }
}