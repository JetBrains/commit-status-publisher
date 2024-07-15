package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.groups.Tuple.tuple;

@Test
public class CommitStatusPublisherFeatureTest extends CommitStatusPublisherTestBase {
  protected CommitStatusPublisherFeature myFeature;
  protected CommitStatusPublisherFeatureController myFeatureController;
  protected PublisherSettingsController mySettingsController;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    WebControllerManager wcm = new MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    final PublisherManager publisherManager = new PublisherManager(myServer);
    mySettingsController = new PublisherSettingsController(wcm, pluginDescr, publisherManager, myProjectManager, myFixture.getSingletonService(SecurityContext.class));

    myFeatureController = new CommitStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, publisherManager, mySettingsController, myFixture.getSecurityContext());

    myFeature = new CommitStatusPublisherFeature(myFeatureController, publisherManager);
  }

  public void should_not_allow_to_save_feature_without_publisher_selected() {
    PropertiesProcessor processor = myFeature.getParametersProcessor(myBuildType);
    Collection<InvalidProperty> errors = processor.process(map(Constants.VCS_ROOT_ID_PARAM, "someRootId"));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));

    errors = processor.process(map(Constants.VCS_ROOT_ID_PARAM, "someRootId", Constants.PUBLISHER_ID_PARAM, DummyPublisherSettings.ID));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));
  }
}

