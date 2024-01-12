

package jetbrains.buildServer.commitPublisher.gerrit;

import com.jcraft.jsch.JSchException;
import java.io.IOException;
import jetbrains.buildServer.commitPublisher.PublisherException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface does not declare full gerrit functionality, but only the methods required
 * by Commit Status Publisher.
 */
interface GerritClient {

  void review(@NotNull GerritConnectionDetails connectionDetails, @Nullable final String label, @NotNull String vote, @NotNull String message, @NotNull String revision) throws Exception;

  void testConnection(@NotNull GerritConnectionDetails connectionDetails) throws JSchException, IOException, PublisherException;

  String runCommand(@NotNull GerritConnectionDetails connectionDetails, @NotNull String command) throws JSchException, IOException;

}