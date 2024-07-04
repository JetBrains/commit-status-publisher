package jetbrains.buildServer.commitPublisher.gerrit;

import org.jetbrains.annotations.NotNull;

public class GerritConstants {
  public static final String GERRIT_PUBLISHER_ID = "gerritStatusPublisher";
  public static final String GERRIT_SERVER = "gerritServer";
  public static final String GERRIT_PROJECT = "gerritProject";
  public static final String GERRIT_USERNAME = "gerritUsername";
  public static final String GERRIT_SUCCESS_VOTE = "successVote";
  public static final String GERRIT_FAILURE_VOTE = "failureVote";
  public static final String GERRIT_LABEL = "label";

  @NotNull
  public String getGerritProject() {
    return GERRIT_PROJECT;
  }

  @NotNull
  public String getGerritLabel() {
    return GERRIT_LABEL;
  }

  @NotNull
  public String getGerritUsername() {
    return GERRIT_USERNAME;
  }

  @NotNull
  public String getGerritSuccessVote() {
    return GERRIT_SUCCESS_VOTE;
  }

  @NotNull
  public String getGerritFailureVote() {
    return GERRIT_FAILURE_VOTE;
  }

  @NotNull
  public String getGerritServer() {
    return GERRIT_SERVER;
  }
}
