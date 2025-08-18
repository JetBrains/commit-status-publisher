package jetbrains.buildServer.commitPublisher.processor.predicate;

import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface BuildPredicate extends Predicate<SBuild> {
  @Override
  boolean test(@NotNull SBuild build);
}
