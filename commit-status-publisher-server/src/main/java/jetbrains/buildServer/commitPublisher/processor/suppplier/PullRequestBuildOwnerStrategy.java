package jetbrains.buildServer.commitPublisher.processor.suppplier;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

public class PullRequestBuildOwnerStrategy implements BuildOwnerStrategy {

  private final VcsRootUsernamesManager myVcsRootUsernamesManager;

  public PullRequestBuildOwnerStrategy(@NotNull VcsRootUsernamesManager vcsRootUsernamesManager) {
    myVcsRootUsernamesManager = vcsRootUsernamesManager;
  }

  @Override
  @NotNull
  public Collection<SUser> apply(@NotNull SBuild build) {
    @Nullable final String pullRequestAuthor = build.getParametersProvider().get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER);
    Collection<SUser> candidates = new LinkedList<>();
    if (pullRequestAuthor != null) {
      for (final VcsRootInstanceEntry vcsRootEntry : build.getVcsRootEntries()) {
        candidates.addAll(myVcsRootUsernamesManager.getUsers(vcsRootEntry.getVcsRoot(), pullRequestAuthor));
      }
    }
    return candidates;
  }
}
