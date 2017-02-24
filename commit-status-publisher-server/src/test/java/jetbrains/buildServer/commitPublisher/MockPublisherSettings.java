package jetbrains.buildServer.commitPublisher;

import java.util.List;
import java.util.Map;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author anton.zamolotskikh, 13/02/17.
 */
public class MockPublisherSettings extends DummyPublisherSettings {

  static final String PUBLISHER_ID = "MockPublisherId";
  private final CommitStatusPublisherProblems myProblems;
  private CommitStatusPublisher myPublisher = null;
  private List<String> myRootNamesToFailTestConnection = null;

  public MockPublisherSettings(CommitStatusPublisherProblems problems) {
    myProblems = problems;
  }

  @Override
  @NotNull
  public String getId() {
    return PUBLISHER_ID;
  }

  public void setPublisher(CommitStatusPublisher publisher) {
    myPublisher = publisher;
  }

  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return null == myPublisher ? new MockPublisher(this, getId(), buildType, buildFeatureId, params, myProblems, new PublisherLogger()) : myPublisher;
  }

  @Override
  public boolean isEventSupported(final CommitStatusPublisher.Event event) {
    return true; // Mock publisher "supports" all events
  }

  @Override
  public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
    if(null != myRootNamesToFailTestConnection && myRootNamesToFailTestConnection.contains(root.getName())) {
      throw new PublisherException(String.format("Test connection has failed for vcs root %s", root.getName()));
    }
  }

  public void setVcsRootsToFailTestConnection(List<String> rootNames)  {
    myRootNamesToFailTestConnection = rootNames;
  }
}
