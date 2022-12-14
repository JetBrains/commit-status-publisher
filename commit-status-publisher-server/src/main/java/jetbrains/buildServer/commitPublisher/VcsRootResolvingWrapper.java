package jetbrains.buildServer.commitPublisher;

import java.util.Map;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsRootResolvingWrapper implements VcsRoot {

  private final ValueResolver myValueResolver;
  private final VcsRoot myVcsRoot;

  public VcsRootResolvingWrapper(ValueResolver valueResolver, VcsRoot vcsRoot) {
    myValueResolver = valueResolver;
    myVcsRoot = vcsRoot;
  }

  @NotNull
  @Override
  public String describe(boolean verbose) {
    return myVcsRoot.describe(verbose);
  }

  @Override
  public long getId() {
    return myVcsRoot.getId();
  }

  @NotNull
  @Override
  public String getName() {
    return myVcsRoot.getName();
  }

  @NotNull
  @Override
  public String getVcsName() {
    return myVcsRoot.getVcsName();
  }

  @NotNull
  @Override
  public Map<String, String> getProperties() {
    return myValueResolver.resolve(myVcsRoot.getProperties());
  }

  @Nullable
  @Override
  public String getProperty(@NotNull String propertyName) {
    String unresolvedValue = myVcsRoot.getProperty(propertyName);
    if (unresolvedValue == null) {
      return unresolvedValue;
    }
    return myValueResolver.resolve(unresolvedValue).getResult();
  }

  @Nullable
  @Override
  public String getProperty(@NotNull String propertyName, @Nullable String defaultValue) {
    String unresolvedValue = myVcsRoot.getProperty(propertyName);
    if (unresolvedValue == null) {
      return defaultValue;
    }
    return myValueResolver.resolve(unresolvedValue).getResult();
  }

  @NotNull
  @Override
  public String getExternalId() {
    return myVcsRoot.getExternalId();
  }
}
