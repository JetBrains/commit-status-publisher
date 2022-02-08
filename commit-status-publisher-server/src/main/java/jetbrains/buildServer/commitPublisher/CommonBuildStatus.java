package jetbrains.buildServer.commitPublisher;

import java.util.Collections;
import java.util.Map;

public class CommonBuildStatus {
  private final String myBuild;
  private final String myState;
  private final String myDescription;
  private final String myUrl;
  private final Map<String, String> myDomainSpecificAtributes;

  public CommonBuildStatus(String build, String state, String description, String url, Map<String, String> domainSpecificAtributes) {
    myBuild = build;
    myState = state;
    myDescription = description;
    myUrl = url;
    myDomainSpecificAtributes = domainSpecificAtributes == null ? Collections.emptyMap() : domainSpecificAtributes;
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

  public Map<String, String> getDomainSpecificAtributes() {
    return myDomainSpecificAtributes;
  }
}
