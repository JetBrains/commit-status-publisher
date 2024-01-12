

package jetbrains.buildServer.commitPublisher;

import java.util.List;
import java.util.Map;
import jetbrains.buildServer.serverSide.BuildTypeIdentity;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 13/02/17.
 */
public class MockPublisherSettings extends DummyPublisherSettings {

  static final String PUBLISHER_ID = "MockPublisherId";
  private final CommitStatusPublisherProblems myProblems;
  private WebLinks myLinks;
  private CommitStatusPublisher myPublisher = null;
  private List<String> myRootNamesToFailTestConnection = null;
  private boolean myIsFeatureLessPublishingEnabled = false;

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

  public void setLinks(WebLinks links) {
    myLinks = links;
  }

  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return null == myPublisher ? new MockPublisher(this, getId(), buildType, buildFeatureId, params, myProblems, new PublisherLogger(), myLinks) : myPublisher;
  }

  @Override
  public boolean isPublishingForVcsRoot(final VcsRoot vcsRoot) {
    return vcsRoot.getVcsName().equals("jetbrains.git");
  }

  @Override
  public boolean isEventSupported(final CommitStatusPublisher.Event event, final SBuildType buildType, final Map<String, String> params) {
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

  public void enableFeatureLessPublishing() {
    myIsFeatureLessPublishingEnabled = true;
  }

  @Override
  public boolean isFeatureLessPublishingSupported(@NotNull SBuildType buildType) {
    return myIsFeatureLessPublishingEnabled;
  }

  @Nullable
  @Override
  public CommitStatusPublisher createFeaturelessPublisher(@NotNull SBuildType buildType, @NotNull SVcsRoot vcsRoot) {
    return myPublisher != null ? myPublisher : null;
  }
}