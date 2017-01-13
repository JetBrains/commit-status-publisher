package jetbrains.buildServer.commitPublisher.upsource.data;

/**
 * This class does not represent full user information contained in the corresponding JSON response
 */
public class UpsourceCurrentUser {
  public String userId;
  public boolean isServerAdmin;
  public String [] adminPermissionsInProjects;
  public String [] reviewViewPermissionsInProjects;
}
