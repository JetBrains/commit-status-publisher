/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.github.ui;

import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 05.09.12 23:26
 */
public class UpdateChangesConstants {
  public String getServerKey() { return Constants.GITHUB_SERVER; }
  public String getContextKey() { return Constants.GITHUB_CONTEXT; }
  public String getFailOnSizeKey() { return Constants.GITHUB_FAILONSIZE; }
  public String getArtifactsKey() { return Constants.GITHUB_ARTIFACTS; }
  public String getBytesKey() { return Constants.GITHUB_BYTES; }
  public String getPctKey() { return Constants.GITHUB_PCT; }
  public String getUserNameKey() { return Constants.GITHUB_USERNAME; }
  public String getPasswordKey() { return Constants.GITHUB_PASSWORD; }
  public String getAccessTokenKey() { return Constants.GITHUB_TOKEN; }
  public String getAuthenticationTypeKey() { return Constants.GITHUB_AUTH_TYPE;}
  public String getAuthenticationTypePasswordValue() { return GitHubApiAuthenticationType.PASSWORD_AUTH.getValue();}
  public String getAuthenticationTypeTokenValue() { return GitHubApiAuthenticationType.TOKEN_AUTH.getValue();}
}
