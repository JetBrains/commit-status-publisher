package org.jetbrains.teamcity.publisher;

import org.jetbrains.annotations.NotNull;

public class Constants {

  public static final String VCS_ROOT_ID_PARAM = "vcsRootId";
  public static final String PUBLISHER_ID_PARAM = "publisherId";

  @NotNull
  public String getVcsRootIdParam() {
    return VCS_ROOT_ID_PARAM;
  }

  @NotNull
  public String getPublisherIdParam() {
    return PUBLISHER_ID_PARAM;
  }
}
