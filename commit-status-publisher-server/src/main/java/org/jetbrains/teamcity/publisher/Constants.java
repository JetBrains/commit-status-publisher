package org.jetbrains.teamcity.publisher;

import org.jetbrains.annotations.NotNull;

public class Constants {

  public static final String VCS_ROOT_ID_PARAM = "vcsRootId";

  @NotNull
  public String getVcsRootIdParam() {
    return VCS_ROOT_ID_PARAM;
  }

}
