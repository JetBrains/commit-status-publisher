package jetbrains.buildServer.swarm.web;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import jetbrains.buildServer.BaseWebTestCase;
import jetbrains.buildServer.buildTriggers.vcs.BuildRevisionBuilder;
import jetbrains.buildServer.commitPublisher.MockPluginDescriptor;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.controllers.MockRequest;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.swarm.*;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.cache.ResetCacheRegisterImpl;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.swarm.web.SwarmBuildPageExtension.SWARM_BEAN;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class SwarmBuildPageExtensionTest extends BaseWebTestCase {

  enum SwarmReviewsResult {
    EMPTY,
    HANG,
    NORMAL_REVIEWS
  }

  private SwarmBuildPageExtension myExtension;
  private VcsRootInstance myVri;
  private SwarmReviewsResult myReviewsResult;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myReviewsResult = SwarmReviewsResult.NORMAL_REVIEWS;

    SwarmTestUtil.addSwarmFeature(myBuildType, "http://swarm-root/");

    SVcsRootImpl perforce = myFixture.addVcsRoot("perforce", "");
    myVri = myBuildType.getVcsRootInstanceForParent(perforce);
    SwarmClientManager swarmClientManager = new SwarmClientManager(myWebLinks, () -> null, new ResetCacheRegisterImpl()) {
      @NotNull
      @Override
      protected SwarmClient doCreateSwarmClient(@NotNull Map<String, String> params) {
        return createSwarmClient(params);
      }
    };

    myExtension = new SwarmBuildPageExtension(myServer, myWebManager, new MockPluginDescriptor(), swarmClientManager);
  }

  @Test
  public void should_not_be_available_without_swarm_feature() throws Exception {

    myBuildType.removeBuildFeature(myBuildType.getBuildFeatures().iterator().next().getId());
    SwarmBuildPageExtension extension =
      new SwarmBuildPageExtension(myServer, myWebManager, new MockPluginDescriptor(), new SwarmClientManager(myWebLinks, () -> null, new ResetCacheRegisterImpl()));

    SFinishedBuild build = createBuild(Status.NORMAL);
    MockRequest buildRequest = new MockRequest("buildId", String.valueOf(build.getBuildId()));
    then(extension.isAvailable(buildRequest)).isFalse();

    HashMap<String, Object> model = new HashMap<>();
    extension.fillModel(model, buildRequest, build);
    
    then(((SwarmBuildDataBean)model.get(SWARM_BEAN)).isReviewsPresent()).isFalse();
    then(((SwarmBuildDataBean)model.get(SWARM_BEAN)).isHasData()).isFalse();
  }
  
  @Test
  public void should_provide_reviews_data() throws Exception {
    // Given there is a build configuration with perforce VCS Root
    // and with associated Swarm build feature,
    // When swarm client returns associated reviews for the build Perforce changelist ID
    // Then those reviews should be present in the JSP model bean

    test_provide_reviews_data((vri) -> {
      return build().in(myBuildType)
                    .withBuildRevisions(BuildRevisionBuilder.buildRevision(vri, "12321"))
                    .finish();
    });
  }

  @Test
  public void should_provide_reviews_data_personal_swarm() throws Exception {
    // Given there is a build configuration with perforce VCS Root
    // and with associated Swarm build feature,
    // When swarm client returns associated reviews for the shelved changelist ID associated with the build
    // Then those reviews should be present in the JSP model bean

    test_provide_reviews_data((vri) -> {
      return build().in(myBuildType)
                    .personalForUser("kir")
                    .parameter("vcsRoot." + vri.getExternalId() + ".shelvedChangelist", "12321")
                    .withBuildRevisions(BuildRevisionBuilder.buildRevision(vri, "1"))
                    .finish();
    });
  }

  private void test_provide_reviews_data(Function<VcsRootInstance, SFinishedBuild> createBuildWithRevisions) throws PublisherException {
    SFinishedBuild build = createBuildWithRevisions.apply(myVri);
    myExtension.forceLoadReviews(build);

    SwarmBuildDataBean bean = runRequestAndGetBean(myExtension, build);
    then((bean).isReviewsPresent()).isTrue();
    then((bean).isHasData()).isTrue();
    then(bean.getReviews().size()).isEqualTo(1);
    then(bean.getReviews().get(0).getUrl()).isEqualTo("http://swarm-root");
    then(bean.getReviews().get(0).getReviewIds()).containsExactly(380l, 381l, 382l);
  }

  @Test
  public void should_not_load_data_on_simple_page_load() throws Exception {

    myReviewsResult = SwarmReviewsResult.HANG;

    SFinishedBuild build = build().in(myBuildType)
                                  .withBuildRevisions(BuildRevisionBuilder.buildRevision(myVri, "12321"))
                                  .finish();

    SwarmBuildDataBean bean = runRequestAndGetBean(myExtension, build);
    then((bean).isReviewsPresent()).as("Should not preload data").isFalse(); // Data not preloaded
    then((bean).isHasData()).as("Should not preload data").isFalse(); // Data not preloaded
  }

  @Test
  public void should_cache_reviews_and_allow_to_reset_cache() throws Exception {

    SFinishedBuild build = build().in(myBuildType)
                                   .withBuildRevisions(BuildRevisionBuilder.buildRevision(myVri, "12321"))
                                   .finish();
    myExtension.forceLoadReviews(build);

    SwarmBuildDataBean bean1 = runRequestAndGetBean(myExtension, build);
    SwarmBuildDataBean bean2 = runRequestAndGetBean(myExtension, build);

    // Check caching
    then(bean1.getReviews().get(0).getReviews().get(0))
      .as("Should cache review data between recent calls")
      .isSameAs(bean2.getReviews().get(0).getReviews().get(0));

    // Reset cache
    myExtension.forceLoadReviews(build);
    SwarmBuildDataBean bean3 = runRequestAndGetBean(myExtension, build);
    then(bean1.getReviews().get(0).getReviews().get(0))
      .as("Should reset cache")
      .isNotSameAs(bean3.getReviews().get(0).getReviews().get(0));
  }

  @Test
  public void should_return_info_when_no_reviews() throws Exception {

    SFinishedBuild build = build().in(myBuildType)
                                   .withBuildRevisions(BuildRevisionBuilder.buildRevision(myVri, "12321"))
                                   .finish();
    myReviewsResult = SwarmReviewsResult.EMPTY;
    myExtension.forceLoadReviews(build);

    SwarmBuildDataBean bean = runRequestAndGetBean(myExtension, build);

    then(bean.getReviews()).isEmpty();
    then(bean.isReviewsPresent()).isFalse();
    then(bean.isHasData()).isTrue();
  }

  @NotNull
  private SwarmClient createSwarmClient(final Map<String, String> params) {
    return new SwarmClient(myWebLinks, params, 10, null) {
      @NotNull
      @Override
      protected ReviewLoadResponse loadReviews(@NotNull String changelistId, @NotNull String debugInfo) {
        switch (myReviewsResult) {
          case HANG: {
            ThreadUtil.sleep(20000);
            throw new RuntimeException("Problem");
          }
          case EMPTY: return new ReviewLoadResponse(Arrays.asList());
          case NORMAL_REVIEWS: return new ReviewLoadResponse(Arrays.asList(
            new SingleReview(380l, "needsReview"),
            new SingleReview(382l, "needsRevision"),
            new SingleReview(381l, "needsReview")));
          default: throw new RuntimeException("Missing case");
        }
      }
    };
  }

  private static SwarmBuildDataBean runRequestAndGetBean(SwarmBuildPageExtension extension, SFinishedBuild build) {
    HashMap<String, Object> model = new HashMap<>();
    MockRequest buildRequest = new MockRequest("buildId", String.valueOf(build.getBuildId()));
    then(extension.isAvailable(buildRequest)).isTrue();
    extension.fillModel(model, buildRequest, build);

    return (SwarmBuildDataBean)model.get(SWARM_BEAN);
  }

}
