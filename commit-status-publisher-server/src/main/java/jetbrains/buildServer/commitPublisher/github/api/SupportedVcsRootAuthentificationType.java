package jetbrains.buildServer.commitPublisher.github.api;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SupportedVcsRootAuthentificationType {
  REFRESHABLE_TOKEN_AUTH("ACCESS_TOKEN"),
  TOKEN_PASSWORD_AUTH("PASSWORD");
  private final String myValue;

  SupportedVcsRootAuthentificationType(@NotNull String value) {
    myValue = value;
  }

  public String getValue() {
    return myValue;
  }

  public static boolean contains(@Nullable String vcsAuthType) {
    return Arrays.stream(values()).map(v -> v.getValue()).anyMatch(v -> v.equals(vcsAuthType));
  }
}
