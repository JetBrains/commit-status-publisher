

package jetbrains.buildServer.commitPublisher.github.api;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
* Created by Eugene Petrenko (eugene.petrenko@gmail.com)
* Date: 06.09.12 0:13
*/
public enum GitHubChangeState {
  Pending("pending"),
  Success("success"),
  Error("error"),
  Failure("failure");


  private static final Map<String, GitHubChangeState> STATE_MAPPING = Arrays.stream(values()).collect(Collectors.toMap(GitHubChangeState::getState, Function.identity()));

  private final String myState;

  GitHubChangeState(@NotNull final String state) {
    myState = state;
  }

  @NotNull
  public String getState() {
    return myState;
  }

  public static GitHubChangeState getByState(String state) {
    return STATE_MAPPING.get(state);
  }
}