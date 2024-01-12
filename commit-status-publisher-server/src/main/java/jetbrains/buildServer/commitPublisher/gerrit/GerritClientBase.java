

package jetbrains.buildServer.commitPublisher.gerrit;

import com.google.gson.Gson;
import com.jcraft.jsch.JSchException;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.commitPublisher.gerrit.data.GerritProjectInfo;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts review and testConnection calls into Gerrit SSH command line commands.
 */
abstract class GerritClientBase implements GerritClient {

  private static final Pattern ESCAPE_PATTERN = Pattern.compile("[\\\\\\\"]");
  private static final String USE_VERIFIED_OPTION= "$verified-option";
  private final Gson myGson = new Gson();

  @Override
  public void review(@NotNull final GerritConnectionDetails connectionDetails,
                     @Nullable final String label,
                     @NotNull final String vote,
                     @NotNull final String message,
                     @NotNull final String revision)
    throws Exception {

    StringBuilder command = new StringBuilder();
    command.append("gerrit review --project ").append(connectionDetails.getGerritProject())
           .append(buildVoteClause(label)).append(vote)
           .append(" -m \"").append(escape(message)).append("\" ")
           .append(revision);
    IOGuard.allowNetworkCall(() -> runCommand(connectionDetails, command.toString()));
  }

  @Override
  public void testConnection(@NotNull final GerritConnectionDetails connectionDetails) throws JSchException, IOException, PublisherException {
    String output = runCommand(connectionDetails, "gerrit ls-projects --format JSON");
    String gerritProject = connectionDetails.getGerritProject();
    GerritProjectMap myMap = myGson.fromJson(output, GerritProjectMap.class);
    if (null == myMap || !myMap.containsKey(gerritProject)) {
      throw new PublisherException(String.format("Inaccessible Gerrit project %s", gerritProject));
    }
  }

  @NotNull
  private static String buildVoteClause(@Nullable String label) {
    if (USE_VERIFIED_OPTION.equals(label) || TeamCityProperties.getBoolean("teamcity.commitStatusPublisher.gerrit.verified.option"))
      return " --verified ";

    if (null == label || label.isEmpty())
      return " --label Verified=";

    return String.format(" --label %s=", label);
  }

  @NotNull
  private static String escape(@NotNull String s) {
    return ESCAPE_PATTERN.matcher(s).replaceAll("\\\\$0");
  }

  private static class GerritProjectMap extends HashMap<String, GerritProjectInfo> {}

}