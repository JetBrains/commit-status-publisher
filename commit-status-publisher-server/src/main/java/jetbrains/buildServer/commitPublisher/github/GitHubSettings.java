package jetbrains.buildServer.commitPublisher.github;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiFactory;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GitHubSettings implements CommitStatusPublisherSettings {

  private final ChangeStatusUpdater myUpdater;

  public GitHubSettings(@NotNull ChangeStatusUpdater updater) {
    myUpdater = updater;
  }

  @NotNull
  public String getId() {
    return Constants.GITHUB_PUBLISHER_ID;
  }

  @NotNull
  public String getName() {
    return "GitHub";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return "github/githubSettings.jsp";
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    final Map<String, String> result = new HashMap<String, String>();
    final UpdateChangesConstants C = new UpdateChangesConstants();
    result.put(C.getServerKey(), GitHubApiFactory.DEFAULT_URL);
    return result;
  }

  @Nullable
  @Override
  public Map<String, String> transformParameters(@NotNull Map<String, String> params) {
    String securePwd = params.get(Constants.GITHUB_PASSWORD);
    String deprecatedPwd = params.get(Constants.GITHUB_PASSWORD_DEPRECATED);
    if (securePwd == null && deprecatedPwd != null) {
      Map<String, String> result = new HashMap<String, String>(params);
      result.remove(Constants.GITHUB_PASSWORD_DEPRECATED);
      result.put(Constants.GITHUB_PASSWORD, deprecatedPwd);
      return result;
    }
    return null;
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull Map<String, String> params) {
    return new GitHubPublisher(myUpdater, params);
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    return "Update change status into GitHub";
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    final UpdateChangesConstants c = new UpdateChangesConstants();
    return new PropertiesProcessor() {
      private boolean checkNotEmpty(@NotNull final Map<String, String> properties,
                                    @NotNull final String key,
                                    @NotNull final String message,
                                    @NotNull final Collection<InvalidProperty> res) {
        if (isEmpty(properties, key)) {
          res.add(new InvalidProperty(key, message));
          return true;
        }
        return false;
      }

      private boolean isEmpty(@NotNull final Map<String, String> properties,
                              @NotNull final String key) {
        return StringUtil.isEmptyOrSpaces(properties.get(key));
      }

      @NotNull
      public Collection<InvalidProperty> process(@Nullable final Map<String, String> p) {
        final Collection<InvalidProperty> result = new ArrayList<InvalidProperty>();
        if (p == null) return result;

        GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(p.get(c.getAuthenticationTypeKey()));
        if (authenticationType == GitHubApiAuthenticationType.PASSWORD_AUTH) {
          checkNotEmpty(p, c.getUserNameKey(), "Username must be specified", result);
          checkNotEmpty(p, c.getPasswordKey(), "Password must be specified", result);
        }

        if (authenticationType == GitHubApiAuthenticationType.TOKEN_AUTH) {
          checkNotEmpty(p, c.getAccessTokenKey(), "Personal Access Token must be specified", result);
        }

        if (!checkNotEmpty(p, c.getServerKey(), "GitHub api URL", result)) {
          final String url = "" + p.get(c.getServerKey());
          if (!ReferencesResolverUtil.mayContainReference(url) && !(url.startsWith("http://") || url.startsWith("https://"))) {
            result.add(new InvalidProperty(c.getServerKey(), "GitHub api URL should start with http:// or https://"));
          }
        }

        return result;
      }
    };
  }

  public boolean isEnabled() {
    return true;
  }
}
