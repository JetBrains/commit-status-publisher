package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotification;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemTicket;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

public class CommitStatusPublisherProblems {

  private final SystemProblemNotification myProblems;
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, SystemProblemTicket>> myTickets = new ConcurrentHashMap<String, ConcurrentHashMap<String, SystemProblemTicket>> ();
  private final Striped<Lock> myLocks = Striped.lazyWeakLock(256);

  public CommitStatusPublisherProblems(@NotNull SystemProblemNotification systemProblems) {
    myProblems = systemProblems;
  }

  public void reportProblem(@NotNull CommitStatusPublisher publisher,
                     @NotNull String buildDescription,
                     @NotNull Throwable t) {
    String description = "Publish status error in build " + buildDescription + ": ";
    if (t instanceof PublishError) {
      description += t.getMessage();
    } else {
      description += t.toString();
    }
    reportProblem(publisher, description);
  }

  void reportProblem(@NotNull CommitStatusPublisher publisher, @NotNull String problemDescription) {
    SBuildType buildType = publisher.getBuildType();
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      clearProblem(publisher);
      SystemProblem problem = new SystemProblem(problemDescription, null, Constants.COMMIT_STATUS_PUBLISHER_PROBLEM_TYPE, null);
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
        Map<String, SystemProblemTicket> tickets = myTickets.get(btId);
        if (tickets.containsKey(featureId)) {
          SystemProblemTicket ticket = tickets.get(featureId);
          if (ticket != null) {
            ticket.cancel();
            tickets.remove(featureId);
          }
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
        Map<String, SystemProblemTicket> tickets = myTickets.get(btId);
        Set<String> featureIdsToRemove = new HashSet<String>(tickets.keySet());
        featureIdsToRemove.removeAll(currentFeatureIds);
        for (String featureId: featureIdsToRemove) {
          if (tickets.containsKey(featureId)) {
            SystemProblemTicket ticket = tickets.get(featureId);
            ticket.cancel();
            tickets.remove(featureId);
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void putTicket(String buildTypeInternalId, String publisherBuildFeatureId, SystemProblemTicket ticket) {
    ConcurrentHashMap<String, SystemProblemTicket> tickets;
    if (myTickets.containsKey(buildTypeInternalId)) {
      tickets = myTickets.get(buildTypeInternalId);
    } else {
      tickets = new ConcurrentHashMap<String, SystemProblemTicket>();
      myTickets.put(buildTypeInternalId, tickets);
    }
    tickets.put(publisherBuildFeatureId, ticket);
  }
}
