package jetbrains.buildServer.commitPublisher.gerrit;

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.commitPublisher.BaseCommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

class GerritPublisher extends BaseCommitStatusPublisher {

  private final static Logger LOG = Logger.getInstance(GerritPublisher.class.getName());

  private final ServerSshKeyManager mySshKeyManager;
  private final WebLinks myLinks;

  GerritPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId,
                         @Nullable ServerSshKeyManager sshKeyManager,
                         @NotNull WebLinks links,
                         @NotNull Map<String, String> params,
                         @NotNull CommitStatusPublisherProblems problems) {
    super(buildType, buildFeatureId, params, problems);
    mySshKeyManager = sshKeyManager;
    myLinks = links;
  }

  @NotNull
  public String toString() {
    return "gerrit";
  }

  @NotNull
  @Override
  public String getId() {
    return Constants.GERRIT_PUBLISHER_ID;
  }

  @Override
  public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) throws PublisherException {
    Branch branch = build.getBranch();
    if (branch == null || branch.isDefaultBranch())
      return false;

    String vote = build.getBuildStatus().isSuccessful() ? getSuccessVote() : getFailureVote();
    String msg = build.getFullName() +
            " #" + build.getBuildNumber() +
            ": " + build.getStatusDescriptor().getText() +
            " " + myLinks.getViewResultsUrl(build);

    StringBuilder command = new StringBuilder();
    command.append("gerrit review --project ").append(getGerritProject())
           .append(" --label Verified=").append(vote)
           .append(" -m \"").append(msg).append("\" ")
           .append(revision.getRevision());
    try {
      SBuildType bt = build.getBuildType();
      if (null == bt) return false;
      runCommand(bt.getProject(), command.toString());
      return true;
    } catch (Exception e) {
      throw new PublisherException("Cannot publish status to Gerrit for VCS root " +
                                   revision.getRoot().getName() + ": " + e.toString(), e);
    }
  }

  private void runCommand(@NotNull SProject project, @NotNull String command) throws JSchException, IOException {
    ChannelExec channel = null;
    Session session = null;
    try {
      JSch jsch = new JSch();
      addKeys(jsch, project);
      session = createSession(jsch);
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
      channel = (ChannelExec) session.openChannel("exec");
      channel.setPty(false);
      channel.setCommand(command);
      BufferedReader stdout = new BufferedReader(new InputStreamReader(channel.getInputStream()));
      BufferedReader stderr = new BufferedReader(new InputStreamReader(channel.getErrStream()));
      LOG.debug("Run command '" + command + "'");
      channel.connect();
      String out = readFully(stdout);
      String err = readFully(stderr);
      LOG.info("Command '" + command + "' finished, stdout: '" + out + "', stderr: '" + err + "', exitCode: " + channel.getExitStatus());
      if (err.length() > 0)
        throw new IOException(err);
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
    }
  }

  private Session createSession(@NotNull JSch jsch) throws JSchException {
    String server = getGerritServer();
    int idx = server.indexOf(":");
    if (idx != -1) {
      String host = server.substring(0, idx);
      int port = Integer.valueOf(server.substring(idx + 1, server.length()));
      return jsch.getSession(getUsername(), host, port);
    } else {
      return jsch.getSession(getUsername(), server, 29418);
    }
  }

  @NotNull
  private String readFully(@NotNull BufferedReader reader) throws IOException {
    String line;
    StringBuilder out = new StringBuilder();
    while ((line = reader.readLine()) != null) {
      out.append(line).append("\n");
    }
    return out.toString().trim();
  }

  private void addKeys(@NotNull JSch jsch, @NotNull SProject project) throws JSchException {
    String uploadedKeyId = myParams.get(ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP);
    if (uploadedKeyId != null && mySshKeyManager != null) {
      TeamCitySshKey key = mySshKeyManager.getKey(project, uploadedKeyId);
      if (key != null)
        jsch.addIdentity(key.getName(), key.getPrivateKey(), null, null);
    }
    String home = System.getProperty("user.home");
    home = home == null ? new File(".").getAbsolutePath() : new File(home).getAbsolutePath();
    File defaultKey = new File(new File(home, ".ssh"), "id_rsa");
    if (defaultKey.isFile())
      jsch.addIdentity(defaultKey.getAbsolutePath());
  }

  private String getGerritServer() {
    return myParams.get(Constants.GERRIT_SERVER);
  }

  private String getGerritProject() {
    return myParams.get(Constants.GERRIT_PROJECT);
  }

  private String getUsername() {
    return myParams.get(Constants.GERRIT_USERNAME);
  }

  private String getSuccessVote() {
    return myParams.get(Constants.GERRIT_SUCCESS_VOTE);
  }

  private String getFailureVote() {
    return myParams.get(Constants.GERRIT_FAILURE_VOTE);
  }
}
