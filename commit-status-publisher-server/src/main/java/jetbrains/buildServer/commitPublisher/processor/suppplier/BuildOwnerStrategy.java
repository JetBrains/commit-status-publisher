package jetbrains.buildServer.commitPublisher.processor.suppplier;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;

public interface BuildOwnerStrategy extends Function<SBuild, Collection<SUser>> {
  @Override
  @NotNull
  Collection<SUser> apply(@NotNull SBuild build);
}
