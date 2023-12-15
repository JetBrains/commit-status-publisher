package jetbrains.buildServer.commitPublisher;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

@Test
public class CommitStatusesCacheTest extends BaseTestCase {
  private static final String DEFAULT_PREFIX = "prefix";
  private static final String DEFAULT_REVISION = "revision";
  private static final long DEFAULT_ROOT_ID = 1L;
  private static final TestStatus DEFAULT_STATUS = new TestStatus(DEFAULT_PREFIX, "1");
  private static final Function<TestStatus, String> PREFIX_PROVIDER = s -> s.name;

  private CommitStatusesCache<TestStatus> myStatusesCache;
  private AtomicInteger myBatchLoaderCallsCounter;
  private BuildRevision myRevision;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    if (myRevision == null) myRevision = mockBuildRevision();
    myStatusesCache = new CommitStatusesCache<>();
    myBatchLoaderCallsCounter = new AtomicInteger(0);
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    setInternalProperty(CommitStatusesCache.CACHE_MAX_SIZE_PARAMETER, CommitStatusesCache.CACHE_MAX_SIZE_DEFAULT_VALUE);
    setInternalProperty(CommitStatusesCache.CACHE_VALUE_TTL_PARAMETER, String.valueOf(CommitStatusesCache.CACHE_VALUE_TTL_DEFAULT_VALUE_MS));
  }

  public void should_put_to_cache() {
    Supplier<Collection<TestStatus>> batchStatusLoader = getBatchStatusLoader(DEFAULT_STATUS,
                                                                              new TestStatus(DEFAULT_PREFIX + 2, "2"),
                                                                              new TestStatus(DEFAULT_PREFIX + 3, "3")
    );
    TestStatus statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1);
    Assert.assertEquals(statusFromCache, DEFAULT_STATUS);

    statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1, "Value should be taken from cache without reloading");
    Assert.assertEquals(statusFromCache, DEFAULT_STATUS);
  }

  public void should_not_load_missing_status() {
    Supplier<Collection<TestStatus>> batchStatusLoader = getBatchStatusLoader();
    TestStatus statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertNull(statusFromCache);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1);

    statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertNull(statusFromCache);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1, "Missing already requested value should be taken from cahce without reloading");

    statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX + 2, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertNull(statusFromCache);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1, "Missing known value should be taken from cahce without reloading");

    statusFromCache = myStatusesCache.getStatusFromCache(myRevision, "test", batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertNull(statusFromCache);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1, "Missing unknown value should be taken from cahce without reloading");
  }

  public void should_expire_entries() throws Exception {
    forceCacheCleanupToHappen();
    Supplier<Collection<TestStatus>> batchStatusLoader = getBatchStatusLoader(DEFAULT_STATUS);
    TestStatus statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1);
    Assert.assertEquals(statusFromCache, DEFAULT_STATUS);

    sleep(100L);  //here entrie should expire
    statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 2, "Expired entety should be reloaded");
    Assert.assertEquals(statusFromCache, DEFAULT_STATUS);
  }

  public void shoule_expire_missing_entity() throws InterruptedException {
    forceCacheCleanupToHappen();
    Supplier<Collection<TestStatus>> batchStatusLoader = getBatchStatusLoader();
    myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER); // the missing value was added to the cache
    sleep(100L);
    batchStatusLoader = getBatchStatusLoader(DEFAULT_STATUS);
    TestStatus statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 2, "Cache entity should be reloaded");
    Assert.assertEquals(statusFromCache, DEFAULT_STATUS, "New entity should be provided instead of missing one");
  }

  public void should_remove_enteties() {
    Supplier<Collection<TestStatus>> batchStatusLoader = getBatchStatusLoader(DEFAULT_STATUS);
    TestStatus statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 1);
    Assert.assertEquals(statusFromCache, DEFAULT_STATUS);

    myStatusesCache.removeStatusFromCache(myRevision, DEFAULT_PREFIX);
    TestStatus expectedStatus = new TestStatus(DEFAULT_PREFIX, "2");
    batchStatusLoader = getBatchStatusLoader(expectedStatus);
    statusFromCache = myStatusesCache.getStatusFromCache(myRevision, DEFAULT_PREFIX, batchStatusLoader, PREFIX_PROVIDER);
    Assert.assertEquals(myBatchLoaderCallsCounter.get(), 2, "New entity should be loaded, because the old one was removed");
    Assert.assertEquals(statusFromCache, expectedStatus, "Old entity should be replaced with the new one");
  }

  private BuildRevision mockBuildRevision() {
    Mock rootMock = mock(VcsRootInstance.class);
    rootMock.stubs().method("getId").withNoArguments().will(returnValue(DEFAULT_ROOT_ID));
    rootMock.stubs().method("describe").withAnyArguments().will(returnValue("description"));

    return new BuildRevision((VcsRootInstance) rootMock.proxy(), DEFAULT_REVISION, "+:*", null);
  }

  private Supplier<Collection<TestStatus>> getBatchStatusLoader(@NotNull TestStatus ... testStatuses) {
    return () -> {
      myBatchLoaderCallsCounter.incrementAndGet();
      return Lists.newArrayList(testStatuses);
    };
  }

  private void sleep(long timeout) throws InterruptedException {
    synchronized (this) {
      wait(timeout);
    }
  }

  private void forceCacheCleanupToHappen() {
    setInternalProperty(CommitStatusesCache.CACHE_VALUE_TTL_PARAMETER, 50);
    setInternalProperty(CommitStatusesCache.CACHE_VALUE_WILDCARD_TTL_PARAMETER, 50);  //to let entity expire and cleanup to be triggered
  }

  private static final class TestStatus {
    final String name;
    final String payload;

    public TestStatus(String name, String payload) {
      this.name = name;
      this.payload = payload;
    }
  }
}