

package jetbrains.buildServer.commitPublisher.bitbucketCloud;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public enum BitbucketCloudBuildStatus {
  SUCCESSFUL, FAILED, INPROGRESS, STOPPED;

  private static final Map<String, BitbucketCloudBuildStatus> INDEX = Arrays.stream(values()).collect(Collectors.toMap(val -> val.name(), Function.identity()));

  @Nullable
  public static BitbucketCloudBuildStatus getByName(String name) {
    return INDEX.get(name);
  }
}