package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotification;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemTicket;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

public class CommitStatusPublisherProblems {

  private final SystemProblemNotification myProblems;
  private final ConcurrentHashMap<BuildTypePublisher, SystemProblemTicket> myTickets = new ConcurrentHashMap<BuildTypePublisher, SystemProblemTicket>();
  private final Striped<Lock> myLocks = Striped.lazyWeakLock(256);

  public CommitStatusPublisherProblems(@NotNull SystemProblemNotification systemProblems) {
    myProblems = systemProblems;
  }


  void reportProblem(@NotNull SBuildType buildType, @NotNull CommitStatusPublisher publisher, @NotNull String problemDescription) {
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      clearProblem(buildType, publisher);
      SystemProblem problem = new SystemProblem(problemDescription, null, Constants.COMMIT_STATUS_PUBLISHER_PROBLEM_TYPE, null);
      myTickets.put(new BuildTypePublisher(buildType, publisher.getId()), myProblems.raiseProblem(buildType, problem));
    } finally {
      lock.unlock();
    }
  }


  void clearProblem(@NotNull SBuildType buildType, @NotNull CommitStatusPublisher publisher) {
    Lock lock = myLocks.get(buildType);
    lock.lock();
    try {
      SystemProblemTicket ticket = myTickets.get(new BuildTypePublisher(buildType, publisher.getId()));
      if (ticket != null)
        ticket.cancel();
    } finally {
      lock.unlock();
    }
  }


  private final static class BuildTypePublisher extends Pair<SBuildType, String> {
    public BuildTypePublisher(@NotNull SBuildType buildType, @NotNull String publisherId) {
      super(buildType, publisherId);
    }

    @NotNull
    SBuildType buildType() {
      return first;
    }

    @NotNull
    String publisherId() {
      return second;
    }
  }
}
