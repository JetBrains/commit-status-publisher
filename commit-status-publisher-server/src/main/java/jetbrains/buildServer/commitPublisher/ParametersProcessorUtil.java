package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.url.InvalidUriException;
import jetbrains.buildServer.vcshostings.url.ServerURI;
import jetbrains.buildServer.vcshostings.url.ServerURIParser;
import org.jetbrains.annotations.NotNull;

public class ParametersProcessorUtil {
  public static void processVcsRootUrl(@NotNull VcsRoot vcsRoot, Collection<InvalidProperty> errors) {
    try {
      String vcsUrl = vcsRoot.getProperty("url");
      if (vcsUrl != null && StringUtil.isNotEmpty(vcsUrl)) {
        ServerURI vcsUri = ServerURIParser.createServerURI(vcsUrl);
        if (!vcsUri.getScheme().toLowerCase().startsWith("http")) {
          errors.add(new InvalidProperty(Constants.AUTH_TYPE, "Credentials cannot be extracted as the selected VCS root uses non-HTTP(S) fetch URL"));
        }
      }
    } catch (InvalidUriException e) {
      errors.add(new InvalidProperty(Constants.AUTH_TYPE, "Credentials cannot be extracted as the selected VCS root uses non-HTTP(S) fetch URL"));
    }
  }

  public static void processVcsRootAuthMethod(@NotNull VcsRoot vcsRoot, Collection<InvalidProperty> errors) {
    String vcsAuthType = vcsRoot.getProperty(Constants.VCS_AUTH_METHOD);
    if (Constants.VCS_AUTH_METHOD_ANONYMOUS.equalsIgnoreCase(vcsAuthType)) {
      errors.add(new InvalidProperty(Constants.AUTH_TYPE,
                                     "Using anonymous VCS authentication method to extract pull request information is impossible. Please provide an access token"));
    }
  }
}
