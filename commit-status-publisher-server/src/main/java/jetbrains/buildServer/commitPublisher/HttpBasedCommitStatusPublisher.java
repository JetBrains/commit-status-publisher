package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.ExceptionUtil;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;

public abstract class HttpBasedCommitStatusPublisher extends BaseCommitStatusPublisher implements HttpResponseProcessor {

  private static final Logger LOG = Logger.getInstance(HttpBasedCommitStatusPublisher.class.getName());
  private final ExecutorServices myExecutorServices;
  private final HttpResponseProcessor myHttpResponseProcessor;

  public HttpBasedCommitStatusPublisher(@NotNull CommitStatusPublisherSettings settings,
                                        @NotNull SBuildType buildType, @NotNull String buildFeatureId,
                                        @NotNull final ExecutorServices executorServices,
                                        @NotNull Map<String, String> params,
                                        @NotNull CommitStatusPublisherProblems problems) {
    super(settings, buildType, buildFeatureId, params, problems);
    myExecutorServices = executorServices;
    myHttpResponseProcessor = new DefaultHttpResponseProcessor();
  }

  protected void post(final String url, final String username, final String password,
                      final String data, final ContentType contentType, final Map<String, String> headers,
                      final String buildDescription) {
    try {
      IOGuard.allowNetworkCall(() -> HttpHelper.post(url, username, password, data, contentType, headers, getConnectionTimeout(), getSettings().trustStore(), this));
    } catch (Exception ex) {
      myProblems.reportProblem("Commit Status Publisher HTTP request has failed", this, buildDescription, url, ex, LOG);
    }
  }

  @Deprecated // The method is no longer necessary as all commit status publisher methods are already invoked asynchronously
  protected Future postAsync(final String url, final String username, final String password,
                             final String data, final ContentType contentType, final Map<String, String> headers,
                             final String buildDescription) {
    ExecutorService service = myExecutorServices.getLowPriorityExecutorService();
    final HttpResponseProcessor that = this;
    return service.submit(ExceptionUtil.catchAll("posting commit status", new Runnable() {
      @Override
      public void run() {
        try {
          IOGuard.allowNetworkCall(() -> HttpHelper.post(url, username, password, data, contentType, headers, getConnectionTimeout(), getSettings().trustStore(), that));
        } catch (Exception ex) {
          myProblems.reportProblem("Commit Status Publisher HTTP request has failed",
                                   HttpBasedCommitStatusPublisher.this, buildDescription,
                                   url, ex, LOG);
        }
      }
    }));
  }

  public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
    myHttpResponseProcessor.processResponse(response);
  }
}
