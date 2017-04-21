package jetbrains.buildServer.commitPublisher.tfs;

import jetbrains.buildServer.commitPublisher.CommitStatusPublisherProblems;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Controls TFS publisher availability.
 */
public class TfsPublisherConfig {
  @Bean(name = "TfsPublisherSettings")
  @NotNull
  @Conditional(TfsPublisherActivation.class)
  public TfsPublisherSettings getIssueProviderType(@NotNull ExecutorServices executorServices,
                                                   @NotNull PluginDescriptor descriptor,
                                                   @NotNull WebLinks links,
                                                   @NotNull CommitStatusPublisherProblems problems,
                                                   @NotNull OAuthConnectionsManager oauthConnectionsManager,
                                                   @NotNull OAuthTokensStorage oauthTokensStorage,
                                                   @NotNull SecurityContext securityContext,
                                                   @NotNull VcsModificationHistory vcsHistory) {
    return new TfsPublisherSettings(executorServices, descriptor, links, problems,
      oauthConnectionsManager, oauthTokensStorage, securityContext, vcsHistory);
  }
}

/**
 * TFS publisher activation condition.
 */
class TfsPublisherActivation implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return TeamCityProperties.getBoolean(TfsConstants.TFS_PUBLISHER_ENABLE);
  }
}
