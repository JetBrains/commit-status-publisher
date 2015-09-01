package org.jetbrains.teamcity.publisher.gerrit;

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.teamcity.publisher.BaseCommitStatusPublisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class GerritPublisher extends BaseCommitStatusPublisher {

  private final static Logger LOG = Loggers.SERVER;

  private final ServerSshKeyManager mySshKeyManager;
  private final WebLinks myLinks;

  public GerritPublisher(@Nullable ServerSshKeyManager sshKeyManager,
                         @NotNull WebLinks links,
                         @NotNull Map<String, String> params) {
    super(params);
    mySshKeyManager = sshKeyManager;
    myLinks = links;
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    Branch branch = build.getBranch();
    if (branch == null || branch.isDefaultBranch())
      return;

    String vote = build.getBuildStatus().isSuccessful() ? getSuccessVote() : getFailureVote();
    String msg = build.getStatusDescriptor().getText() + " " + myLinks.getViewResultsUrl(build);

    StringBuilder command = new StringBuilder();
    command.append("gerrit review --project ").append(getGerritProject())
           .append(" --label ").append(getGerritLabel()).append("=").append(vote)
           .append(" -m \"").append(msg).append("\" ")
           .append(revision.getRevision());
    try {
      runCommand(build.getBuildType().getProject(), command.toString());
    } catch (Exception e) {
      error("Error while running gerrit command '" + command + "'", e);
      String problemId = "gerrit.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "gerrit.publisher", e.getMessage()));
    }
  }

  private void runCommand(@NotNull SProject project, @NotNull String command) throws JSchException, IOException {
    ChannelExec channel = null;
    Session session = null;
    try {
      JSch jsch = new JSch();
      addKeys(jsch, project);
      session = jsch.getSession(getUsername(), getGerritServer(), 29418);
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
      channel = (ChannelExec) session.openChannel("exec");
      channel.setPty(false);
      channel.setCommand(command);
      BufferedReader stdout = new BufferedReader(new InputStreamReader(channel.getInputStream()));
      BufferedReader stderr = new BufferedReader(new InputStreamReader(channel.getErrStream()));
      log("Run command '" + command + "'");
      channel.connect();
      String out = readFully(stderr);
      String err = readFully(stderr);
      log("Command '" + command + "' finished, stdout: '" + out + "', stderr: '" + err + "', exitCode: " + channel.getExitStatus());
      if (err.length() > 0)
        throw new IOException(err);
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
    }
  }

  @NotNull
  private String readFully(@NotNull BufferedReader reader) throws IOException {
    String line;
    StringBuilder out = new StringBuilder();
    while ((line = reader.readLine()) != null) {
      out.append(line);
    }
    return out.toString();
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

  String getGerritServer() {
    return myParams.get("gerritServer");
  }

  String getGerritProject() {
    return myParams.get("gerritProject");
  }

  private String getUsername() {
    return myParams.get("gerritUsername");
  }

  private String getGerritLabel() {
    return myParams.get("gerritLabel");
  }

  private String getSuccessVote() {
    return myParams.get("successVote");
  }

  private String getFailureVote() {
    return myParams.get("failureVote");
  }

  private void log(@NotNull String message) {
    if (TeamCityProperties.getBoolean("commitStatusPublisher.gerrit.enableInfoLog")) {
      LOG.info(message);
    } else {
      LOG.debug(message);
    }
  }

  private void error(@NotNull String message, @NotNull Exception error) {
    Loggers.SERVER.error(message, error);
  }
}
