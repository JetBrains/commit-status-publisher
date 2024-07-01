package jetbrains.buildServer.commitPublisher.gitea;

import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.vcshostings.http.credentials.AccessTokenCredentials;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.jetbrains.annotations.NotNull;

public class GiteaAccessTokenCredentials extends AccessTokenCredentials implements HttpCredentials {

  private static final String HEADER_AUTHORIZATION = "Authorization";

  private static final String AUTHORIZATION_TOKEN_PREFIX = "token ";

  public GiteaAccessTokenCredentials(@NotNull final String token) {
    super(token);
  }

  @Override
  public void set(@NotNull final HTTPRequestBuilder requestBuilder) {
    requestBuilder.withHeader(HEADER_AUTHORIZATION, AUTHORIZATION_TOKEN_PREFIX + getToken());
  }
}
