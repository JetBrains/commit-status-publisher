package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class PropertyChecks {

  private PropertyChecks() {}

  public static void mandatoryString(@NotNull String name,
                                     @NotNull String errorReason,
                                     @NotNull Map<String, String> params,
                                     @NotNull Collection<InvalidProperty> errors) {
    final String value = params.get(name);
    if (StringUtil.isEmptyOrSpaces(value)) {
      errors.add(new InvalidProperty(name, errorReason));
    }
  }

}
