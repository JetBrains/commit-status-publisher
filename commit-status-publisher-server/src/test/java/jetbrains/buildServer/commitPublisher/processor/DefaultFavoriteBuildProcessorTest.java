package jetbrains.buildServer.commitPublisher.processor;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.processor.predicate.BuildPredicate;
import jetbrains.buildServer.commitPublisher.processor.strategy.BuildOwnerSupplier;
import jetbrains.buildServer.favoriteBuilds.FavoriteBuildsManager;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.users.UserSet;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@Test
public class DefaultFavoriteBuildProcessorTest extends BaseTestCase {
  private SUser myTrueUser;
  private SUser myFalseUser;
  private SBuild myNotSupportedBuild;
  private SBuild mySupportedBuild;
  private FavoriteBuildsManager myFavoriteBuildsManager;
  private BuildOwnerSupplier myBuildOwnerSupplier;
  private BuildPredicate myBuildPredicate;
  private DefaultFavoriteBuildProcessor myFavoriteBuildProcessor;
  private final Date today = new Date();

  @Override
  @BeforeMethod
  protected void setUp() throws Exception {
    super.setUp();

    final String username = "test";
    myTrueUser = Mockito.mock(SUser.class);
    myFalseUser = Mockito.mock(SUser.class);
    final PropertyKey userMarkImportantBuildProperty = new SimplePropertyKey(Constants.USER_AUTOMATICALLY_MARK_IMPORTANT_BUILDS_AS_FAVORITE_INTERNAL_PROPERTY);
    when(myTrueUser.getBooleanProperty(userMarkImportantBuildProperty)).thenReturn(true);
    when(myFalseUser.getBooleanProperty(Mockito.any())).thenReturn(false);

    myNotSupportedBuild = Mockito.mock(SBuild.class);
    mySupportedBuild = Mockito.mock(SBuild.class);
    final ParametersProvider parametersProvider = Mockito.mock(ParametersProvider.class);
    when(myNotSupportedBuild.getParametersProvider()).thenReturn(parametersProvider);
    when(mySupportedBuild.getParametersProvider()).thenReturn(parametersProvider);
    when(myNotSupportedBuild.getParametersProvider().get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER)).thenReturn(null);
    when(mySupportedBuild.getParametersProvider().get(Constants.BUILD_PULL_REQUEST_AUTHOR_PARAMETER)).thenReturn(username);
    final BuildPromotion buildPromotion = Mockito.mock(BuildPromotion.class);
    when(mySupportedBuild.getBuildPromotion()).thenReturn(buildPromotion);
    when(myNotSupportedBuild.getBuildPromotion()).thenReturn(buildPromotion);
    when(mySupportedBuild.getStartDate()).thenReturn(today);
    when(myNotSupportedBuild.getStartDate()).thenReturn(today);
    when(mySupportedBuild.getBuildId()).thenReturn(1L);
    when(myNotSupportedBuild.getBuildId()).thenReturn(2L);

    myBuildOwnerSupplier = Mockito.mock(BuildOwnerSupplier.class);
    when(myBuildOwnerSupplier.apply(mySupportedBuild)).thenReturn(new HashSet<>(Collections.singletonList(myTrueUser)));

    myBuildPredicate = Mockito.mock(BuildPredicate.class);
    when(myBuildPredicate.test(mySupportedBuild)).thenReturn(true);
    when(myBuildPredicate.test(myNotSupportedBuild)).thenReturn(false);

    myFavoriteBuildsManager = Mockito.mock(FavoriteBuildsManager.class);
    doNothing().when(myFavoriteBuildsManager).tagBuild(Mockito.any(), Mockito.any());

    myFavoriteBuildProcessor = new DefaultFavoriteBuildProcessor(myFavoriteBuildsManager);
  }

  public void should_mark_only_when_user_checkbox_is_true() {
    assertTrue(myFavoriteBuildProcessor.shouldMarkAsFavorite(myTrueUser));
    assertFalse(myFavoriteBuildProcessor.shouldMarkAsFavorite(myFalseUser));
  }

  public void should_filter_build_only_when_supported() {
    assertFalse(myFavoriteBuildProcessor.isSupported(myNotSupportedBuild, myBuildPredicate));
    assertTrue(myFavoriteBuildProcessor.isSupported(mySupportedBuild, myBuildPredicate));
  }


  public void should_mark_as_favorite_when_checkbox_is_true_and_build_is_supported() {
    myFavoriteBuildProcessor.markAsFavorite(mySupportedBuild, myBuildOwnerSupplier);
    Mockito.verify(mySupportedBuild, Mockito.atLeastOnce()).getBuildPromotion();
    Mockito.verify(myFavoriteBuildsManager, Mockito.times(1)).tagBuild(mySupportedBuild.getBuildPromotion(), myTrueUser);
  }

  public void should_not_mark_as_favorite_when_checkbox_is_false_and_build_is_supported() {
    when(myBuildOwnerSupplier.apply(mySupportedBuild)).thenReturn(new HashSet<>(Collections.singletonList(myFalseUser)));
    myFavoriteBuildProcessor.markAsFavorite(mySupportedBuild, myBuildOwnerSupplier);
    Mockito.verify(myFavoriteBuildsManager, Mockito.never()).tagBuild(mySupportedBuild.getBuildPromotion(), myFalseUser);
  }
}
