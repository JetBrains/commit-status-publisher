package jetbrains.buildServer.commitPublisher.processor.strategy;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface BuildOwnerSupplier {
  @NotNull
  Set<SUser> supplyFrom(@NotNull final SBuild build);
}
