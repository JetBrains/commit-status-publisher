

package jetbrains.buildServer.commitPublisher.tfs;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class TfsRepositoryInfoTest {

  @Test(dataProvider = "repositoryData")
  public void testRepositoryUrlParsing(final String repositoryUrl, final TfsRepositoryInfo info) {
    TfsRepositoryInfo repositoryInfo = TfsRepositoryInfo.parse(repositoryUrl);
    if (info == null) {
      Assert.assertNull(repositoryInfo);
    } else {
      Assert.assertNotNull(repositoryInfo);
      Assert.assertEquals(repositoryInfo, info);
    }
  }

  @Test(dataProvider = "sshRootWithHintData")
  public void testUseServerUrlHintForSshRoots(final String repositoryUrl, final String hintUrl, final TfsRepositoryInfo info) {
    TfsRepositoryInfo repositoryInfo = TfsRepositoryInfo.parse(repositoryUrl, hintUrl);

    Assert.assertNotNull(repositoryInfo);
    Assert.assertEquals(repositoryInfo, info);
  }

  @DataProvider
  public Object[][] repositoryData() {
    return new Object[][]{
      {"https://test.visualstudio.com/DefaultCollection/_git/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com/DefaultCollection", "Repository", null)
      },
      {"https://test.visualstudio.com/_git/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com", "Repository", null)
      },
      {" https://test.visualstudio.com/DefaultCollection/_git/Repository ",
        new TfsRepositoryInfo("https://test.visualstudio.com/DefaultCollection", "Repository", null)
      },
      {"https://test.visualstudio.com/DefaultCollection/Project/_git/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com/DefaultCollection", "Repository", "Project")
      },
      {"https://test.visualstudio.com/Project/_git/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com", "Repository", "Project")
      },
      {"http://host:81/tfs/DefaultCollection/Project/_git/Repository",
        new TfsRepositoryInfo("http://host:81/tfs/DefaultCollection", "Repository", "Project")
      },
      {"http://host:81/DefaultCollection/Project/_git/Repository",
        new TfsRepositoryInfo("http://host:81/DefaultCollection", "Repository", "Project")
      },
      {"http://host:81/CustomCollection/_git/Repository",
        new TfsRepositoryInfo("http://host:81/CustomCollection", "Repository", null)
      },
      {null, null},
      {"", null},
      {" ", null},
      {"ssh://test@vs-ssh.visualstudio.com:22/DefaultCollection/Project/_ssh/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com/DefaultCollection", "Repository", "Project")
      },
      {"ssh://test@test.visualstudio.com:22/DefaultCollection/Project/_git/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com/DefaultCollection", "Repository", "Project")
      },
      {"ssh://host:22/DefaultCollection/_git/Repository", null},
      {
        "ssh://git@ssh.dev.azure.com:v3/org/Project/Repository",
        new TfsRepositoryInfo("https://dev.azure.com/org", "Repository", "Project")
      },
      {
        "https://org@dev.azure.com/org/Project/_git/Repository",
        new TfsRepositoryInfo("https://dev.azure.com/org", "Repository", "Project")
      },
      {
        "https://dev.azure.com/org/_git/Project",
        new TfsRepositoryInfo("https://dev.azure.com/org", "Project", "Project")
      },
      {
        "http://host:81/tfs/CustomCollection/_git/Repository",
        new TfsRepositoryInfo("http://host:81/tfs/CustomCollection", "Repository", null)
      },
      {
        "http://host:81/tfs/CustomCollection/Project/_git/Repository",
        new TfsRepositoryInfo("http://host:81/tfs/CustomCollection", "Repository", "Project")
      },
      {
        "git@ssh.dev.azure.com:v3/org/Project/Repository",
        new TfsRepositoryInfo("https://dev.azure.com/org", "Repository", "Project")
      },
      {
        "git@vs-ssh.visualstudio.com:v3/test/Project/Repository",
        new TfsRepositoryInfo("https://test.visualstudio.com", "Repository", "Project")
      },
    };
  }

  @DataProvider
  public Object[][] sshRootWithHintData() {
    return new Object[][]{
      {
        "ssh://host:22/DefaultCollection/_git/Project",
        "http://host:81/DefaultCollection",
        new TfsRepositoryInfo("http://host:81/DefaultCollection", "Project", null)
      },
      {
        "ssh://host:22/DefaultCollection/Project/_git/Repository",
        "http://host:81",
        new TfsRepositoryInfo("http://host:81/DefaultCollection", "Repository", "Project")
      },
      {
        "ssh://host:22/CustomCollection/Project/_git/Repository",
        "http://host:81",
        new TfsRepositoryInfo("http://host:81/CustomCollection", "Repository", "Project")
      }
    };
  }
}