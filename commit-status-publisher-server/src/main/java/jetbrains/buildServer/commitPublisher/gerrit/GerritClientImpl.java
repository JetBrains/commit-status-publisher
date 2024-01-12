

package jetbrains.buildServer.commitPublisher.gerrit;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Collection;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public class GerritClientImpl extends GerritClientBase implements GerritClient {

  private final ExtensionHolder myExtensionHolder;

  public GerritClientImpl(@NotNull ExtensionHolder extensionHolder) {
    myExtensionHolder = extensionHolder;
    initJSchConfig();
  }

  public String runCommand(@NotNull GerritConnectionDetails connectionDetails, @NotNull String command) throws JSchException, IOException {
    ChannelExec channel = null;
    Session session = null;
    String out = null;
    try {
      JSch jsch = new JSch();
      addKeys(jsch, connectionDetails.getProject(), connectionDetails.getKeyId());
      session = createSession(jsch, connectionDetails.getServer(), connectionDetails.getUserName());
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
      channel = (ChannelExec) session.openChannel("exec");
      channel.setPty(false);
      channel.setCommand(command);
      BufferedReader stdout = new BufferedReader(new InputStreamReader(channel.getInputStream()));
      BufferedReader stderr = new BufferedReader(new InputStreamReader(channel.getErrStream()));
      LOG.debug("Run command '" + command + "'");
      channel.connect();
      out = readFully(stdout);
      String err = readFully(stderr);
      LOG.info("Command '" + command + "' finished, exitCode: " + channel.getExitStatus());
      LOG.debug("Command '" + command + "' has returned stdout: '" + out + "', stderr: '" + err + "'");
      if (err.length() > 0)
        throw new IOException(err);
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
    }
    return out;
  }

  private void addKeys(@NotNull JSch jsch, @NotNull SProject project, @Nullable String keyId) throws JSchException {
    Collection<ServerSshKeyManager> extensions = myExtensionHolder.getExtensions(ServerSshKeyManager.class);
    ServerSshKeyManager sshKeyManager;
    if (extensions.isEmpty()) {
      sshKeyManager = null;
    } else {
      sshKeyManager = extensions.iterator().next();
    }
    if (keyId != null && sshKeyManager != null) {
      TeamCitySshKey key = sshKeyManager.getKey(project, keyId);
      if (key != null)
        jsch.addIdentity(key.getName(), key.getPrivateKey(), null, null);
    }
    String home = System.getProperty("user.home");
    home = home == null ? new File(".").getAbsolutePath() : new File(home).getAbsolutePath();
    File defaultKey = new File(new File(home, ".ssh"), "id_rsa");
    if (defaultKey.isFile())
      jsch.addIdentity(defaultKey.getAbsolutePath());
  }


  private Session createSession(@NotNull JSch jsch, @NotNull String server, @NotNull String username) throws JSchException {
    int idx = server.indexOf(":");
    if (idx != -1) {
      String host = server.substring(0, idx);
      int port = Integer.valueOf(server.substring(idx + 1));
      return jsch.getSession(username, host, port);
    } else {
      return jsch.getSession(username, server, 29418);
    }
  }

  @NotNull
  private static String readFully(@NotNull BufferedReader reader) throws IOException {
    String line;
    StringBuilder out = new StringBuilder();
    while ((line = reader.readLine()) != null) {
      out.append(line).append("\n");
    }
    return out.toString().trim();
  }


  private void initJSchConfig() {
    try {
      Class initializer = Class.forName("jetbrains.buildServer.util.jsch.JSchConfigInitializer");
      Method initMethod = initializer.getMethod("initJSchConfig", Class.class);
      initMethod.invoke(null, JSch.class);
    } catch (ClassNotFoundException e) {
      LOG.warn("Could not find 'jetbrains.buildServer.util.jsch.JSchConfigInitializer' class in the classpath, skip JSch config initialization");
    } catch (NoSuchMethodException e) {
      LOG.warn("Could not find initJSchConfig method in 'jetbrains.buildServer.util.jsch.JSchConfigInitializer' class, skip JSch config initialization, error: " + e.toString());
    } catch (Throwable e) {
      LOG.warn("Failed to perform JSch config initialization, error: " + e.toString());
    }
  }
}