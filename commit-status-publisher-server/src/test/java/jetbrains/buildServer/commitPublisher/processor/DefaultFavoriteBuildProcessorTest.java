package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerSupplier;
import jetbrains.buildServer.favoriteBuilds.FavoriteBuildsManager;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import static org.mockito.Mockito.*;

@Test
public class DefaultFavoriteBuildProcessorTest extends BaseTestCase {
  private SUser myTrueUser;
  private SUser myFalseUser;
  private SBuild mySupportedBuild;
  private FavoriteBuildsManager myFavoriteBuildsManager;
  private BuildOwnerSupplier myBuildOwnerSupplier;
  private DefaultFavoriteBuildProcessor myFavoriteBuildProcessor;
  private BuildPromotionEx myBuildPromotion;

  @Override
  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();

    final String username = "test";
    myTrueUser = Mockito.mock(SUser.class);
    myFalseUser = Mockito.mock(SUser.class);
    final PropertyKey userMarkImportantBuildProperty = new SimplePropertyKey(Constants.USER_AUTO_FAVORITE_IMPORTANT_BUILDS_PROPERTY);
    when(myTrueUser.getBooleanProperty(userMarkImportantBuildProperty)).thenReturn(true);
    when(myFalseUser.getBooleanProperty(Mockito.any())).thenReturn(false);

    //TODO: Refactor all this
    // Parameters provider
    mySupportedBuild = Mockito.mock(SBuild.class);
    myBuildPromotion = Mockito.mock(BuildPromotionEx.class);
    final ParametersProvider parametersProvider = Mockito.mock(ParametersProvider.class);
    when(mySupportedBuild.getParametersProvider()).thenReturn(parametersProvider);
    when(mySupportedBuild.getParametersProvider().get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER)).thenReturn(username);
    when(mySupportedBuild.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singletonList(Mockito.mock(SBuildFeatureDescriptor.class)));
    when(mySupportedBuild.isFinished()).thenReturn(false);
    // Build Promotion Info
    when(mySupportedBuild.getBuildPromotion()).thenReturn(myBuildPromotion);
    when(myBuildPromotion.getTagDatas()).thenReturn(Collections.emptyList());
    when(myBuildPromotion.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singletonList(Mockito.mock(SBuildFeatureDescriptor.class)));

    when(mySupportedBuild.getBuildPromotion()).thenReturn(myBuildPromotion);

    myBuildOwnerSupplier = Mockito.mock(BuildOwnerSupplier.class);
    when(myBuildOwnerSupplier.apply(mySupportedBuild)).thenReturn(new HashSet<>(Collections.singletonList(myTrueUser)));
    myFavoriteBuildsManager = Mockito.mock(FavoriteBuildsManager.class);
    doNothing().when(myFavoriteBuildsManager).tagBuild(Mockito.any(), Mockito.any());

    myFavoriteBuildProcessor = new DefaultFavoriteBuildProcessor(myFavoriteBuildsManager);
  }

  public void should_not_mark_build_that_are_finished() {
    final SBuild finishedBuild = Mockito.mock(SBuild.class);
    when(finishedBuild.isFinished()).thenReturn(true);
    assertFalse(myFavoriteBuildProcessor.markAsFavorite(finishedBuild, myBuildOwnerSupplier));
  }

  public void should_not_mark_build_without_cps_build_feature_enabled() {
    final SBuild buildWithoutCPS= Mockito.mock(SBuild.class);
    final BuildPromotion buildPromotion = Mockito.mock(BuildPromotion.class);
    when(buildWithoutCPS.isFinished()).thenReturn(false);
    when(buildPromotion.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.emptyList());
    when(buildWithoutCPS.getBuildPromotion()).thenReturn(buildPromotion);
    assertFalse(myFavoriteBuildProcessor.markAsFavorite(buildWithoutCPS, myBuildOwnerSupplier));
  }

  public void should_not_mark_builds_that_are_already_tagged() {
    final SBuild taggedBuild = Mockito.mock(SBuild.class);
    final BuildPromotion buildPromotion = Mockito.mock(BuildPromotion.class);
    when(taggedBuild.isFinished()).thenReturn(false);
    when(taggedBuild.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singletonList(Mockito.mock(SBuildFeatureDescriptor.class)));
    when(buildPromotion.getTagDatas()).thenReturn(Collections.singletonList(TagData.createPrivateTag(FavoriteBuildsManager.FAVORITE_BUILD_TAG, Mockito.mock(SUser.class))));
    when(taggedBuild.getBuildPromotion()).thenReturn(buildPromotion);
    assertFalse(myFavoriteBuildProcessor.markAsFavorite(taggedBuild, myBuildOwnerSupplier));
  }

  public void should_not_mark_build_with_one_of_its_dependent_builds_have_cps_build_feature_enabled() {
    final SBuild notSupportedBuild = Mockito.mock(SBuild.class);
    final BuildPromotionEx buildPromotion = Mockito.mock(BuildPromotionEx.class);
    when(notSupportedBuild.isFinished()).thenReturn(false);
    when(buildPromotion.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singletonList(Mockito.mock(SBuildFeatureDescriptor.class)));
    when(buildPromotion.getTagDatas()).thenReturn(Collections.emptyList());
    doAnswer(invocation -> {
      final DependencyConsumer<BuildPromotionEx> buildPromotionExDependencyConsumer = invocation.getArgument(0);
      final BuildPromotionEx innerBuildPromotionEx = Mockito.mock(BuildPromotionEx.class);
      when(innerBuildPromotionEx.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.singletonList(Mockito.mock(SBuildFeatureDescriptor.class)));
      assertEquals(DependencyConsumer.Result.STOP,  buildPromotionExDependencyConsumer.consume(innerBuildPromotionEx));
      return null;
    }).when(buildPromotion).traverseDependedOnMe(Mockito.any());
    when(notSupportedBuild.getBuildPromotion()).thenReturn(buildPromotion);
    assertFalse(myFavoriteBuildProcessor.markAsFavorite(notSupportedBuild, myBuildOwnerSupplier));
  }

  public void should_mark_when_build_is_running_has_cps_feature_enabled_is_not_marked_as_favorite_and_no_dependent_builds_have_cps_enabled() {
    doAnswer(invocation -> {
      final DependencyConsumer<BuildPromotionEx> buildPromotionExDependencyConsumer = invocation.getArgument(0);
      final BuildPromotionEx innerBuildPromotionEx = Mockito.mock(BuildPromotionEx.class);
      when(innerBuildPromotionEx.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE)).thenReturn(Collections.emptyList());
      assertEquals(DependencyConsumer.Result.CONTINUE,  buildPromotionExDependencyConsumer.consume(innerBuildPromotionEx));
      return null;
    }).when(myBuildPromotion).traverseDependedOnMe(Mockito.any());
    assertTrue(myFavoriteBuildProcessor.markAsFavorite(mySupportedBuild, myBuildOwnerSupplier));
  }

  public void should_return_false_if_no_users_are_provided_or_checkbox_is_false() {
    doAnswer(invocation -> null).when(myBuildPromotion).traverseDependedOnMe(Mockito.any());
    when(myBuildOwnerSupplier.apply(Mockito.any())).thenReturn(new HashSet<>(Collections.singletonList(myFalseUser)));
    assertFalse(myFavoriteBuildProcessor.markAsFavorite(mySupportedBuild, myBuildOwnerSupplier));
    when(myBuildOwnerSupplier.apply(Mockito.any())).thenReturn(Collections.emptySet());
    assertFalse(myFavoriteBuildProcessor.markAsFavorite(mySupportedBuild, myBuildOwnerSupplier));
  }
}
