package jetbrains.buildServer.commitPublisher;

public class CommonBuildStatus {
  private final String myBuild;
  private final String myState;
  private final String myDescription;
  private final String myUrl;

  public CommonBuildStatus(String build, String state, String description, String url) {
    myBuild = build;
    myState = state;
    myDescription = description;
    myUrl = url;
  }

  public String getBuild() {
    return myBuild;
  }

  public String getState() {
    return myState;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getUrl() {
    return myUrl;
  }

  @Override
  public String toString() {
    return "CommonBuildStatus{" +
           "myBuild='" + myBuild + '\'' +
           ", myState='" + myState + '\'' +
           ", myDescription='" + myDescription + '\'' +
           ", myUrl='" + myUrl +
           '}';
  }
}
