package jetbrains.buildServer.commitPublisher.processor.strategy;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;

public interface BuildOwnerSupplier extends Function<SBuild, Set<SUser>> {
  @Override
  @NotNull
  Set<SUser> apply(@NotNull final SBuild build);
}
