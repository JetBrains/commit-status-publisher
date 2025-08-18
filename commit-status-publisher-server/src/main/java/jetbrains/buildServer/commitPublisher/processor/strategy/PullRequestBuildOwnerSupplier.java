package jetbrains.buildServer.commitPublisher.processor.strategy;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * This {@link BuildOwnerSupplier} class uses information given by the PullRequest plugin,
 * in order to work this supplier takes from the build's parameters the pull request author.
 * If the Pull Request plugin is not enabled on this build, then it will always return an empty collection.
 */
public class PullRequestBuildOwnerSupplier implements BuildOwnerSupplier {

  private final VcsRootUsernamesManager myVcsRootUsernamesManager;

  public PullRequestBuildOwnerSupplier(@NotNull final VcsRootUsernamesManager vcsRootUsernamesManager) {
    myVcsRootUsernamesManager = vcsRootUsernamesManager;
  }

  @Override
  @NotNull
  public Set<SUser> apply(@NotNull final SBuild build) {
    @Nullable final String pullRequestAuthor = build.getParametersProvider().get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER);
    final Set<SUser> candidates = new HashSet<>();
    if (pullRequestAuthor != null) {
      for (final VcsRootInstanceEntry vcsRootEntry : build.getVcsRootEntries()) {
        candidates.addAll(myVcsRootUsernamesManager.getUsers(vcsRootEntry.getVcsRoot(), pullRequestAuthor));
      }
    }
    return candidates;
  }
}
