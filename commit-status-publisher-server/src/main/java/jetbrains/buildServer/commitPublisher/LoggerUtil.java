package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.http.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoggerUtil {

  public static final String LOG_CATEGORY = "jetbrains.buildServer.COMMIT_STATUS";
  public static final Logger LOG = Logger.getInstance(LOG_CATEGORY);


  public static void logRequest(@NotNull String publisherId,
                                @NotNull HttpMethod method,
                                @NotNull String uri,
                                @Nullable String requestEntity) {
    if (!LOG.isDebugEnabled()) return;

    if (requestEntity == null) {
      requestEntity = "<none>";
    }

    LOG.debug("Calling " + publisherId + " with:\n" +
            "  requestURL: " + uri + "\n" +
            "  requestMethod: " + method + "\n" +
            "  requestEntity: " + requestEntity
    );
  }
}
