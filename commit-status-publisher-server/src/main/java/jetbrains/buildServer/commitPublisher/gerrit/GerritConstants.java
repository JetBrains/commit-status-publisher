package jetbrains.buildServer.commitPublisher.gerrit;

import org.jetbrains.annotations.NotNull;

public class GerritConstants {
  public static final String STATUS_PUBLISHER = "gerritStatusPublisher";
  public static final String SERVER = "gerritServer";
  public static final String PROJECT = "gerritProject";
  public static final String USERNAME = "gerritUsername";
  public static final String SUCCESS_VOTE = "successVote";
  public static final String FAILURE_VOTE = "failureVote";
  public static final String LABEL = "label";

  @NotNull
  public String getProject() {
    return PROJECT;
  }

  @NotNull
  public String getLabel() {
    return LABEL;
  }

  @NotNull
  public String getUsername() {
    return USERNAME;
  }

  @NotNull
  public String getSuccessVote() {
    return SUCCESS_VOTE;
  }

  @NotNull
  public String getFailureVote() {
    return FAILURE_VOTE;
  }

  @NotNull
  public String getServer() {
    return SERVER;
  }
}
