package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.TestFor;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;

import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.groups.Tuple.tuple;

@Test
public class CommitStatusPublisherFeatureTest {

  private CommitStatusPublisherFeature myFeature;

  @BeforeMethod
  public void setUp() throws Exception {
    Mockery context = new Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    CommitStatusPublisherFeatureController controller = context.mock(CommitStatusPublisherFeatureController.class);
    PublisherManager publisher = context.mock(PublisherManager.class);
    myFeature = new CommitStatusPublisherFeature(controller, publisher);
  }


  @TestFor(issues = "TW-41888")
  public void should_not_allow_to_save_feature_without_selected_vcs_root() {
    PropertiesProcessor processor = myFeature.getParametersProcessor();
    Collection<InvalidProperty> errors = processor.process(map(Constants.PUBLISHER_ID_PARAM, "somePublisherId"));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.VCS_ROOT_ID_PARAM, "Choose a VCS root"));
  }


  public void should_not_allow_to_save_feature_without_publisher_selected() {
    PropertiesProcessor processor = myFeature.getParametersProcessor();
    Collection<InvalidProperty> errors = processor.process(map(Constants.VCS_ROOT_ID_PARAM, "someRootId"));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));

    errors = processor.process(map(Constants.VCS_ROOT_ID_PARAM, "someRootId", Constants.PUBLISHER_ID_PARAM, DummyPublisherSettings.ID));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));
  }
}
