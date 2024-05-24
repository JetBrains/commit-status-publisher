

package jetbrains.buildServer.commitPublisher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcshostings.http.HttpHelper;
import jetbrains.buildServer.vcshostings.http.HttpResponseProcessor;
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;

public abstract class HttpBasedCommitStatusPublisher<Status> extends BaseCommitStatusPublisher implements HttpResponseProcessor<HttpPublisherException> {

  private final HttpResponseProcessor<HttpPublisherException> myHttpResponseProcessor;
  protected final WebLinks myLinks;

  public HttpBasedCommitStatusPublisher(@NotNull CommitStatusPublisherSettings settings,
                                        @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                                        @NotNull Map<String, String> params,
                                        @NotNull CommitStatusPublisherProblems problems, WebLinks links) {
    super(settings, buildType, buildFeatureId, params, problems);
    myLinks = links;
    myHttpResponseProcessor = new DefaultHttpResponseProcessor();
  }

  @Override
  protected WebLinks getLinks() {
    return myLinks;
  }

  protected void postJson(@NotNull final String url,
                          @Nullable final HttpCredentials credentials,
                          @Nullable final String data,
                          @Nullable final Map<String, String> headers,
                          @NotNull final String buildDescription) throws PublisherException {
    try {
      LoggerUtil.logRequest(getId(), HttpMethod.POST, url, data);
      IOGuard.allowNetworkCall(
        () -> HttpHelper.post(url, credentials, data, ContentType.APPLICATION_JSON, headers, getConnectionTimeout(), getSettings().trustStore(), new RetryResponseProcessor(this))
      );
    } catch (Exception ex) {
      PublisherException e = new PublisherException("Commit Status Publisher POST HTTP request has failed. " + ex, ex);
      RetryResponseProcessor.processNetworkException(ex, e);
      throw e;
    }
  }

  @Nullable
  protected <T> T get(@NotNull final String url,
                     @Nullable final HttpCredentials credentials,
                     @Nullable final Map<String, String> headers,
                     @NotNull final ResponseEntityProcessor<T> responseProcessor) throws PublisherException {
    try {
      LoggerUtil.logRequest(getId(), HttpMethod.GET, url, null);
      IOGuard.allowNetworkCall(() -> HttpHelper.get(url, credentials, headers, getConnectionTimeout(), getSettings().trustStore(), new RetryResponseProcessor(responseProcessor)));
      return responseProcessor.getProcessingResult();
    } catch (Exception ex) {
      PublisherException e = new PublisherException("Commit Status Publisher HTTP request has failed", ex);
      RetryResponseProcessor.processNetworkException(ex, e);
      throw e;
    }
  }

  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    myHttpResponseProcessor.processResponse(response);
  }

  protected static String encodeParameter(@NotNull String key, @NotNull String value) {
    try {
      return key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      LOG.warn(String.format("Failed to encode URL parameter \"%s\" value: \"%s\"", key, value), e);
      return key + "=" + value;
    }
  }

  @NotNull
  protected String getApiUrlFromVcsRootUrl(@Nullable String vcsRootUrl) throws PublisherException {
    String url = vcsRootUrl;
    if (url == null) {
      List<SVcsRoot> roots = myBuildType.getVcsRoots();
      if (roots.size() == 0) throw new PublisherException("Could not find VCS Root to extract URL");

      url = roots.get(0).getProperty("url");
      if (StringUtil.isEmptyOrSpaces(url)) throw new PublisherException("Could not find VCS Root URL to transform it into GitLab API URL");
    }

    String apiUrl = getSettings().guessApiURL(url);
    if (StringUtil.isEmptyOrSpaces(apiUrl)) throw new PublisherException("Could not transform VCS Root URL into GitLab API URL");

    return apiUrl;
  }

}