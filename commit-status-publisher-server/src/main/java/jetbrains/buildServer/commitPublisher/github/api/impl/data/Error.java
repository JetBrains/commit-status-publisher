package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

public class Error {
  @Nullable
  public String resource;
  @Nullable
  public String code;
  @Nullable
  public String message;
}
