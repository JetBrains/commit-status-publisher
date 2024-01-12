

package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.systemProblems.BuildFeatureProblemsTicketManager;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommitStatusPublisherProblems {

  private final BuildFeatureProblemsTicketManager myTicketManager;

  public CommitStatusPublisherProblems(@NotNull BuildFeatureProblemsTicketManager ticketManager) {
    myTicketManager = ticketManager;
  }

  public void reportProblem(@NotNull CommitStatusPublisher publisher,
                            @NotNull String buildDescription,
                            @Nullable String destination,
                            @Nullable Throwable t,
                            @NotNull Logger logger) {
    reportProblem("Commit Status Publisher error", publisher, buildDescription, destination, t, logger);
  }

  public void reportProblem(@NotNull String errorMessage,
                              @NotNull CommitStatusPublisher publisher,
                              @NotNull String buildDescription,
                              @Nullable String destination,
                              @Nullable Throwable t,
                              @NotNull Logger logger) {

    String dst = (null == destination) ? "" : "(" + destination + ")";
    String errorDescription = String.format("%s. Publisher: %s%s.", errorMessage, publisher.getId(), dst);
    String logEntry = String.format("%s. Build: %s", errorDescription, buildDescription);
    if (null != t) {
      String exMsg = t.getMessage();
      if (null != exMsg) {
        errorDescription += " " + exMsg;
      } else {
        errorDescription += " " + t.toString();
      }
      logger.warnAndDebugDetails(logEntry, t);
    } else {
      logger.warn(logEntry);
    }
    SBuildType buildType = publisher.getBuildType();
    SystemProblem problem = new SystemProblem(errorDescription, null, Constants.COMMIT_STATUS_PUBLISHER_PROBLEM_TYPE, null);

    myTicketManager.reportProblem(buildType, publisher.getBuildFeatureId(), problem);
  }

  public void clearObsoleteProblems(@NotNull SBuildType buildType) {
    myTicketManager.clearObsoleteProblems(buildType);
  }

  void clearProblem(@NotNull CommitStatusPublisher publisher) {
    SBuildType buildType = publisher.getBuildType();
    String featureId = publisher.getBuildFeatureId();
    myTicketManager.clearProblems(buildType, featureId);
  }
}