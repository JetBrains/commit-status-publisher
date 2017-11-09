package jetbrains.buildServer.commitPublisher.stash;

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum StashAuthenticationType {
  TOKEN_AUTH("token"),
  PASSWORD_AUTH("password"),
  ;

  private final String myValue;


  StashAuthenticationType(@NotNull final String value) {
    myValue = value;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  @NotNull
  public static StashAuthenticationType parse(@Nullable final String value) {
    //migration
    if (value == null || StringUtil.isEmptyOrSpaces(value)) return PASSWORD_AUTH;

    for (StashAuthenticationType v : values()) {
      if (v.getValue().equals(value)) return v;
    }

    throw new IllegalArgumentException("Failed to parse StashAuthenticationType: " + value);
  }
}
