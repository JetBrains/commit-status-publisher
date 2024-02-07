package jetbrains.buildServer.swarm.commitPublisher;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Set;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherTestBase;
import jetbrains.buildServer.serverSide.MockServerPluginDescriptior;
import jetbrains.buildServer.swarm.SwarmClientManager;
import jetbrains.buildServer.util.cache.ResetCacheRegisterImpl;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

public class SwarmPublisherSettingsTest extends CommitStatusPublisherTestBase {

  private SwarmPublisherSettings mySettings;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    final SSLTrustStoreProvider trustStoreProvider = () -> null;
    final SwarmClientManager clientManager = new SwarmClientManager(myWebLinks, trustStoreProvider, new ResetCacheRegisterImpl());
    mySettings = new SwarmPublisherSettings(new MockServerPluginDescriptior(), myFixture.getWebLinks(), myProblems, trustStoreProvider, clientManager);
  }

  @Test
  public void createPublisher_comment_on_all_events_when_feature_is_disabled() {
    setInternalProperty("teamcity.internal.swarm.enableCommentsSelectively", false);
    final CommitStatusPublisher publisher = mySettings.createPublisher(myBuildType, "my-bf", Collections.emptyMap());

    then(publisher).isNotNull();
    then(publisher).isInstanceOf(SwarmPublisher.class);
    final SwarmPublisher swarmPublisher = (SwarmPublisher)publisher;
    then(swarmPublisher.getCommentOnEvents()).containsAll(allSupportedEvents());
  }

  @Test
  public void createPublisher_comment_on_all_events_by_default() {
    setInternalProperty("teamcity.internal.swarm.enableCommentsSelectively", true);
    final CommitStatusPublisher publisher = mySettings.createPublisher(myBuildType, "my-bf", Collections.emptyMap());

    then(publisher).isNotNull();
    then(publisher).isInstanceOf(SwarmPublisher.class);
    final SwarmPublisher swarmPublisher = (SwarmPublisher)publisher;
    then(swarmPublisher.getCommentOnEvents()).containsAll(allSupportedEvents());
  }

  @Test
  public void createPublisher_comment_on_all_events_when_enabled() {
    setInternalProperty("teamcity.internal.swarm.enableCommentsSelectively", true);
    final CommitStatusPublisher publisher = mySettings.createPublisher(myBuildType, "my-bf", ImmutableMap.of(
      SwarmPublisherSettings.PARAM_COMMENT_ON_EVENTS, "true"
    ));

    then(publisher).isNotNull();
    then(publisher).isInstanceOf(SwarmPublisher.class);
    final SwarmPublisher swarmPublisher = (SwarmPublisher)publisher;
    then(swarmPublisher.getCommentOnEvents()).containsAll(allSupportedEvents());
  }

  @Test
  public void createPublisher_comment_on_no_events_when_disabled() {
    setInternalProperty("teamcity.internal.swarm.enableCommentsSelectively", true);
    final CommitStatusPublisher publisher = mySettings.createPublisher(myBuildType, "my-bf", ImmutableMap.of(
      SwarmPublisherSettings.PARAM_COMMENT_ON_EVENTS, "false"
    ));

    then(publisher).isNotNull();
    then(publisher).isInstanceOf(SwarmPublisher.class);
    final SwarmPublisher swarmPublisher = (SwarmPublisher)publisher;
    then(swarmPublisher.getCommentOnEvents()).isEmpty();
  }

  @DataProvider
  public static Object[][] argsForConfiguredEvents() {
    return new Object[][]{
      {"FINISHED", new CommitStatusPublisher.Event[]{CommitStatusPublisher.Event.FINISHED}},
      {"STARTED, QUEUED", new CommitStatusPublisher.Event[]{CommitStatusPublisher.Event.STARTED, CommitStatusPublisher.Event.QUEUED}},
      {"", new CommitStatusPublisher.Event[]{}}
    };
  }

  @Test(dataProvider = "argsForConfiguredEvents")
  public void createPublisher_comment_only_on_configured_events(@NotNull String configuredEventTypes, CommitStatusPublisher.Event[] expectedEvents) {
    setInternalProperty("teamcity.internal.swarm.enableCommentsSelectively", true);
    setInternalProperty("teamcity.internal.swarm.commentOnEventTypes", configuredEventTypes);
    final CommitStatusPublisher publisher = mySettings.createPublisher(myBuildType, "my-bf", Collections.emptyMap());

    then(publisher).isNotNull();
    then(publisher).isInstanceOf(SwarmPublisher.class);
    final SwarmPublisher swarmPublisher = (SwarmPublisher)publisher;
    then(swarmPublisher.getCommentOnEvents()).containsOnly(expectedEvents);
  }


  @Test
  public void createPublisher_comment_on_misconfigured_does_not_fail() {
    setInternalProperty("teamcity.internal.swarm.enableCommentsSelectively", true);
    setInternalProperty("teamcity.internal.swarm.commentOnEventTypes", "THIS_DOES_NOT_EXIST, QUEUED");
    final CommitStatusPublisher publisher = mySettings.createPublisher(myBuildType, "my-bf", Collections.emptyMap());

    then(publisher).isNotNull();
    then(publisher).isInstanceOf(SwarmPublisher.class);
    final SwarmPublisher swarmPublisher = (SwarmPublisher)publisher;
    then(swarmPublisher.getCommentOnEvents()).containsOnly(CommitStatusPublisher.Event.QUEUED);
  }

  private Set<CommitStatusPublisher.Event> allSupportedEvents() {
    return mySettings.getSupportedEvents(myBuildType, Collections.emptyMap());
  }
}