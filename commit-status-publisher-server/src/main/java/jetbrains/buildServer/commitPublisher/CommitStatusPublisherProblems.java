package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotification;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemTicket;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import org.jetbrains.annotations.Nullable;

public class CommitStatusPublisherProblems {

  private final SystemProblemNotification myProblems;
  private final ConcurrentHashMap<String, Map<String, Set<SystemProblemTicket>>> myTickets = new ConcurrentHashMap<String, Map<String, Set<SystemProblemTicket>>> ();
  private final Striped<Lock> myLocks = Striped.lazyWeakLock(256);

  public CommitStatusPublisherProblems(@NotNull SystemProblemNotification systemProblems) {
    myProblems = systemProblems;
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
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      SystemProblem problem = new SystemProblem(errorDescription, null, Constants.COMMIT_STATUS_PUBLISHER_PROBLEM_TYPE, null);
      putTicket(buildType.getInternalId(), publisher.getBuildFeatureId(), myProblems.raiseProblem(buildType, problem));
    } finally {
      lock.unlock();
    }
  }

  void clearProblem(@NotNull CommitStatusPublisher publisher) {
    SBuildType buildType = publisher.getBuildType();
    String featureId = publisher.getBuildFeatureId();
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      String btId = buildType.getInternalId();
      if (myTickets.containsKey(btId)) {
        Map<String, Set<SystemProblemTicket>> ticketsForPublishers = myTickets.get(btId);
        if (ticketsForPublishers.containsKey(featureId)) {
          Set<SystemProblemTicket> tickets = ticketsForPublishers.get(featureId);
          for (SystemProblemTicket ticket: tickets) {
              ticket.cancel();
          }
          ticketsForPublishers.remove(featureId);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  void clearObsoleteProblems(@NotNull SBuildType buildType, @NotNull Collection<String> currentFeatureIds) {
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      String btId = buildType.getInternalId();
      if (myTickets.containsKey(btId)) {
        Map<String, Set<SystemProblemTicket>> ticketsForPublishers = myTickets.get(btId);
        Set<String> featureIdsToRemove = new HashSet<String>(ticketsForPublishers.keySet());
        featureIdsToRemove.removeAll(currentFeatureIds);
        for (String featureId: featureIdsToRemove) {
          if (ticketsForPublishers.containsKey(featureId)) {
            Set<SystemProblemTicket> tickets = ticketsForPublishers.get(featureId);
            for (SystemProblemTicket ticket: tickets) {
              ticket.cancel();
            }
            ticketsForPublishers.remove(featureId);
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void putTicket(String buildTypeInternalId, String publisherBuildFeatureId, SystemProblemTicket ticket) {
    Map<String, Set<SystemProblemTicket>> ticketsForPublishers;
    if (myTickets.containsKey(buildTypeInternalId)) {
      ticketsForPublishers = myTickets.get(buildTypeInternalId);
    } else {
      ticketsForPublishers = new HashMap<String, Set<SystemProblemTicket>>();
      myTickets.put(buildTypeInternalId, ticketsForPublishers);
    }

    Set<SystemProblemTicket> tickets;

    if (ticketsForPublishers.containsKey(publisherBuildFeatureId)) {
      tickets = ticketsForPublishers.get(publisherBuildFeatureId);
    } else {
      tickets = new HashSet<SystemProblemTicket>();
      ticketsForPublishers.put(publisherBuildFeatureId, tickets);
    }
    tickets.add(ticket);
  }
}
