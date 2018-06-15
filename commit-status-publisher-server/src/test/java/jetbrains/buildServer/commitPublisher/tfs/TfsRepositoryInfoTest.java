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
      Assert.assertEquals(repositoryInfo.getServer(), info.getServer());
      Assert.assertEquals(repositoryInfo.getProject(), info.getProject());
      Assert.assertEquals(repositoryInfo.getRepository(), info.getRepository());
    }
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
      {"ssh://host:22/DefaultCollection/_git/Repository", null}
    };
  }
}
