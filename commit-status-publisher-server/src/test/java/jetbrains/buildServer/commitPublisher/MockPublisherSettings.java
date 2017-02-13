package jetbrains.buildServer.commitPublisher;

import java.util.Map;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * @author anton.zamolotskikh, 13/02/17.
 */
public class MockPublisherSettings extends DummyPublisherSettings {

  private final CommitStatusPublisherProblems myProblems;
  private CommitStatusPublisher myPublisher;

  public MockPublisherSettings(CommitStatusPublisherProblems problems) {
    myProblems = problems;
    myPublisher = null;
  }

  @Override
  @NotNull
  public String getId() {
    return CommitStatusPublisherTestBase.PUBLISHER_ID;
  }

  public void setPublisher(CommitStatusPublisher publisher) {
    myPublisher = publisher;
  }

  @Override
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    return null == myPublisher ? new MockPublisher(this, getId(), buildType, buildFeatureId, params, myProblems) : myPublisher;
  }

  @Override
  public boolean isEventSupported(final CommitStatusPublisher.Event event) {
    return true; // Mock publisher "supports" all events
  }
}
