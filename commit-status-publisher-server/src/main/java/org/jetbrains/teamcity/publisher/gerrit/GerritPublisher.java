package org.jetbrains.teamcity.publisher.gerrit;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.publisher.BaseCommitStatusPublisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class GerritPublisher extends BaseCommitStatusPublisher {

  public GerritPublisher(@NotNull Map<String, String> params) {
    super(params);
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build) {
    Branch branch = build.getBranch();
    if (branch == null || branch.isDefaultBranch())
      return;

    BuildRevision revision = getBuildRevisionForVote(build);
    if (revision == null)
      return;

    String vote = build.getBuildStatus().isSuccessful() ? getSuccessVote() : getFailureVote();
    String msg = build.getBuildStatus().isSuccessful() ? "Successful build" : "Failed build";

    StringBuilder command = new StringBuilder();
    command.append("gerrit review --project ").append(getGerritProject())
           .append(" --verified ").append(vote)
           .append(" -m \"").append(msg).append("\" ")
           .append(revision.getRevision());
    try {
      runCommand(command.toString());
    } catch (Exception e) {
      String problemId = "gerrit.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "gerrit.publisher", e.getMessage()));
    }
  }

  private void runCommand(@NotNull String command) throws JSchException, IOException {
    ChannelExec channel = null;
    Session session = null;
    try {
      JSch jsch = new JSch();
      String home = System.getProperty("user.home");
      home = home == null ? new File(".").getAbsolutePath() : new File(home).getAbsolutePath();
      jsch.addIdentity(new File(new File(home, ".ssh"), "id_rsa").getAbsolutePath());
      session = jsch.getSession(getUsername(), getGerritServer(), 29418);
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(command);
      BufferedReader stderr = new BufferedReader(new InputStreamReader(channel.getErrStream()));
      channel.connect();

      String line;
      StringBuilder details = new StringBuilder();
      while ((line = stderr.readLine()) != null) {
        details.append(line).append("\n");
      }
      if (details.length() > 0)
        throw new IOException(details.toString());
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
    }
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

  private String getSuccessVote() {
    return myParams.get("successVote");
  }

  private String getFailureVote() {
    return myParams.get("failureVote");
  }
}
