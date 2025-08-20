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
 * The constant BUILD_PULL_REQUEST_AUTHOR_PARAMETER is set by the PullRequest plugin, more in particular this value
 * contains the contributor username of the pull request. This value (the contributor username) is matched with a TeamCity user using the
 * {@link VcsRootUsernamesManager} class, by using the method getUsers. We collect all the users retrieved in a Set, because
 * It is possible to have the same user repeated (in case the same contributor username name is specified in multiple VCS usernames for the same user).
 * The {@link VcsRootUsernamesManager} by default checks the usernames defined in: defaultKeys, usernameForVcs and usernameForRoot.
 */
public class PullRequestBuildOwnerSupplier implements BuildOwnerSupplier {

  private final VcsRootUsernamesManager myVcsRootUsernamesManager;

  public PullRequestBuildOwnerSupplier(@NotNull final VcsRootUsernamesManager vcsRootUsernamesManager) {
    myVcsRootUsernamesManager = vcsRootUsernamesManager;
  }

  @Override
  @NotNull
  public Set<SUser> supplyFrom(@NotNull final SBuild build) {
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
