package jetbrains.buildServer.commitPublisher.processor.predicate;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.serverSide.*;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.mockito.Mockito.*;

@Test
public class CommitStatusPublisherBuildPredicateTest extends BaseTestCase {
  private SBuild myBuildWithCPS;
  private SBuild myBuildWithoutCPS;
  private BuildPredicate myBuildPredicate;
  private BuildPromotionEx myBuildPromotionWithCPS;


  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBuildWithCPS = Mockito.mock(SBuild.class);
    myBuildWithoutCPS = Mockito.mock(SBuild.class);
    myBuildPromotionWithCPS = Mockito.mock(BuildPromotionEx.class);
    BuildPromotionEx myBuildPromotionWithoutCPS = Mockito.mock(BuildPromotionEx.class);

    when(myBuildWithCPS.getBuildPromotion()).thenReturn(myBuildPromotionWithCPS);
    when(myBuildWithoutCPS.getBuildPromotion()).thenReturn(myBuildPromotionWithoutCPS);

    when(myBuildWithCPS.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singleton(Mockito.mock(SBuildFeatureDescriptor.class)));
    when(myBuildWithoutCPS.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.emptySet());

    when(myBuildPromotionWithCPS.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singleton(Mockito.mock(SBuildFeatureDescriptor.class)));

    myBuildPredicate = new CommitStatusPublisherBuildPredicate();
  }

  public void should_not_accept_build_without_csp_plugin() {
    assertFalse(myBuildPredicate.test(myBuildWithoutCPS));
    assertTrue(myBuildPredicate.test(myBuildWithCPS));
  }

  public void should_decline_when_build_has_cps_and_a_dependent_build_has_cps_plugin() {
    doAnswer(invocation -> {
      DependencyConsumer<BuildPromotionEx> consumer = invocation.getArgument(0);
      final BuildPromotionEx buildPromotionEx = Mockito.mock(BuildPromotionEx.class);
      when(buildPromotionEx.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singleton(Mockito.mock(SBuildFeatureDescriptor.class)));
      assertEquals(DependencyConsumer.Result.STOP, consumer.consume(buildPromotionEx));
      return null;
    }).when(myBuildPromotionWithCPS).traverseDependedOnMe(Mockito.any());
    assertFalse(myBuildPredicate.test(myBuildWithCPS));
  }

  public void should_accept_when_build_has_cps_and_no_dependent_builds_have_cps_plugin() {
    when(myBuildWithCPS.getBuildPromotion()).thenReturn(myBuildPromotionWithCPS);
    doAnswer(invocation -> {
      DependencyConsumer<BuildPromotionEx> consumer = invocation.getArgument(0);
      final BuildPromotionEx buildPromotionEx = Mockito.mock(BuildPromotionEx.class);
      when(buildPromotionEx.getDependedOnMe()).thenReturn(Collections.emptyList());
      when(buildPromotionEx.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.emptySet());
      assertEquals(DependencyConsumer.Result.CONTINUE, consumer.consume(buildPromotionEx));
      return null;
    }).when(myBuildPromotionWithCPS).traverseDependedOnMe(Mockito.any());
    assertTrue(myBuildPredicate.test(myBuildWithCPS));
  }

}