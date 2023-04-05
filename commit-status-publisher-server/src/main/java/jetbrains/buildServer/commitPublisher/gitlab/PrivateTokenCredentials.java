package jetbrains.buildServer.commitPublisher.gitlab;

import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.jetbrains.annotations.NotNull;

public class PrivateTokenCredentials implements HttpCredentials {

  private static final String HEADER = "PRIVATE-TOKEN";

  @NotNull private final String myToken;

  public PrivateTokenCredentials(@NotNull String token) {
    myToken = token;
  }

  @Override
  public void set(@NotNull HTTPRequestBuilder requestBuilder) {
    requestBuilder.withHeader(HEADER, myToken);
  }

}
