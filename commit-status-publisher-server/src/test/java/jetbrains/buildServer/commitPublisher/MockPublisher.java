package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author anton.zamolotskikh, 13/09/16.
 */
class MockPublisher extends BaseCommitStatusPublisher implements CommitStatusPublisher {


  private String myType;
  private String myVcsRootId;


  MockPublisher(@NotNull CommitStatusPublisherSettings settings,
                @NotNull String publisherType,
                @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                @NotNull Map<String, String> params,
                @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
    myType = publisherType;
    myVcsRootId = "MyDefaultVcsRootId";
  }

  @Nullable
  @Override
  public String getVcsRootId() {
    return myVcsRootId;
  }

  void setVcsRootId(String vcsRootId) {
    myVcsRootId = vcsRootId;
  }

  @NotNull
  @Override
  public String getId() {
    return myType;
  }

}
