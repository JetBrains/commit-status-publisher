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


  void reportProblem(@NotNull SBuildType buildType,  @NotNull String buildFeatureId, @NotNull String problemDescription) {
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      clearProblem(buildType, buildFeatureId);
      SystemProblem problem = new SystemProblem(problemDescription, null, Constants.COMMIT_STATUS_PUBLISHER_PROBLEM_TYPE, null);
      putTicket(buildType.getInternalId(), buildFeatureId, myProblems.raiseProblem(buildType, problem));
    } finally {
      lock.unlock();
    }
  }


  void clearProblem(@NotNull SBuildType buildType, @NotNull String buildFeatureId) {
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      String btId = buildType.getInternalId();
      if (myTickets.containsKey(btId)) {
        Map<String, SystemProblemTicket> tickets = myTickets.get(btId);
        if (tickets.containsKey(buildFeatureId)) {
          SystemProblemTicket ticket = tickets.get(buildFeatureId);
          if (ticket != null) {
            ticket.cancel();
            tickets.remove(buildFeatureId);
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
