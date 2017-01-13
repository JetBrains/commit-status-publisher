package jetbrains.buildServer.commitPublisher.gerrit;

import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anton.zamolotskikh, 09/01/17.
 */
class GerritConnectionDetails {
  private final SProject myProject;
  private final String myGerritProject;
  private final String myServer;
  private final String myUserName;
  private final String myKeyId;

  GerritConnectionDetails(@NotNull SProject project, @NotNull String gerritProject,
                                 @NotNull String server, @NotNull String username, @Nullable String keyId) {
    myProject = project;
    myGerritProject = gerritProject;
    myServer = server;
    myUserName = username;
    myKeyId = keyId;
  }

  @NotNull
  SProject getProject() {
    return myProject;
  }

  @NotNull
  String getGerritProject() {
    return myGerritProject;
  }

  @NotNull
  String getServer() {
    return myServer;
  }

  @NotNull
  String getUserName() {
    return myUserName;
  }

  @Nullable
  String getKeyId() {
    return myKeyId;
  }

}
