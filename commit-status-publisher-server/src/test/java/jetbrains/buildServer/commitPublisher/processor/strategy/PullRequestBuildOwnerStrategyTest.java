package jetbrains.buildServer.commitPublisher.processor.strategy;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
import jetbrains.buildServer.vcs.VcsRootUsernamesManager;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

@Test
public class PullRequestBuildOwnerStrategyTest extends BaseTestCase {
  private SBuild myBuildWithParameter;
  private SBuild myBuildWithoutParameter;
  private SUser myUser;
  private BuildOwnerStrategy myBuildOwnerStrategy;
  private final String username = "test";

  @Override
  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();
    myBuildWithoutParameter = Mockito.mock(SBuild.class);
    myBuildWithParameter = Mockito.mock(SBuild.class);
    myUser = Mockito.mock(SUser.class);

    when(myUser.getName()).thenReturn(username);

    final ParametersProvider emptyParametersProvider = Mockito.mock(ParametersProvider.class);
    final ParametersProvider parametersProvider = Mockito.mock(ParametersProvider.class);
    when(myBuildWithoutParameter.getParametersProvider()).thenReturn(emptyParametersProvider);
    when(myBuildWithParameter.getParametersProvider()).thenReturn(parametersProvider);

    when(emptyParametersProvider.get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER)).thenReturn(null);
    when(parametersProvider.get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER)).thenReturn(username);

    final VcsRootInstanceEntry vcsRootInstanceEntry = Mockito.mock(VcsRootInstanceEntry.class);
    final VcsRootInstance vcsRootInstance = Mockito.mock(VcsRootInstance.class);
    when(vcsRootInstanceEntry.getVcsRoot()).thenReturn(vcsRootInstance);
    when(myBuildWithParameter.getVcsRootEntries()).thenReturn(Collections.singletonList(vcsRootInstanceEntry));

    final VcsRootUsernamesManager vcsRootUsernamesManager = Mockito.mock(VcsRootUsernamesManager.class);
    when(vcsRootUsernamesManager.getUsers(Mockito.eq(vcsRootInstance), Mockito.eq(username))).thenReturn(Collections.singletonList(myUser));


    myBuildOwnerStrategy = new PullRequestBuildOwnerStrategy(vcsRootUsernamesManager);
  }

  public void should_return_collection_only_when_pull_request_author_parameter_is_true() {
    assertEmpty(myBuildOwnerStrategy.apply(myBuildWithoutParameter));
    final Collection<SUser> userList = myBuildOwnerStrategy.apply(myBuildWithParameter);
    assertEquals(1, userList.size());
    assertContains(userList, myUser);
  }
}
