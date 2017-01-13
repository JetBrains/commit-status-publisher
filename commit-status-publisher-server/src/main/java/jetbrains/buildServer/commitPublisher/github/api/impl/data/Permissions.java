package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

public class Permissions {
  @Nullable
  public boolean admin;

  @Nullable
  public boolean push;

  @Nullable
  public boolean pull;
}
