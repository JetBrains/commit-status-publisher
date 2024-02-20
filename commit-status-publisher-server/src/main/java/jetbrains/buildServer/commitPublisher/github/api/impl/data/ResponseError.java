package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResponseError {
  @Nullable
  public String message;

  @NotNull
  public List<Error> errors = Collections.emptyList();
}