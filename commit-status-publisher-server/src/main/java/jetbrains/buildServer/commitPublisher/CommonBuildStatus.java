package jetbrains.buildServer.commitPublisher;

import java.util.Collections;
import java.util.Map;

public class CommonBuildStatus {
  private String myBuild;
  private String myState;
  private String myDescription;
  private String myUrl;
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

  public void setBuild(String build) {
    myBuild = build;
  }

  public String getState() {
    return myState;
  }

  public void setState(String state) {
    myState = state;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getUrl() {
    return myUrl;
  }

  public void setUrl(String url) {
    myUrl = url;
  }

  public Map<String, String> getDomainSpecificAtributes() {
    return myDomainSpecificAtributes;
  }
}
