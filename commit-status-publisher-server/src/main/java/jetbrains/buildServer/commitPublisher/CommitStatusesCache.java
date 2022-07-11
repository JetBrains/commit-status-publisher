package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommitStatusesCache<T> {
  private static final String CACHE_VALUE_TTL_PAPRAMETER = "teamcity.commitStatusPublisher.statusCache.ttl";
  private static final long CAHCE_VALUE_TTL_DEFAULT_VALUE_MS = 300_000L;
  private static final String CACHE_FEATURE_TOGGLE_PARAMETER = "teamcity.commitStatusPublisher.statusCache.enabled";

  private final Map<String, ValueWithTTL<T>> myCache = new ConcurrentHashMap<>();
  protected final Striped<Lock> myCacheLocks = Striped.lazyWeakLock(256);

  @Nullable
  private T getStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix) {
    ValueWithTTL<T> value = myCache.get(buildKey(revision, prefix));
    if (value != null && value.isAlive()) {
      return value.getValue();
    }
    return null;
  }

  /**
   * Returns status from the cache. If there was no status in the cache for provided revision, it tries to load a status using provided supplier
   * @param revision key for the cache
   * @param prefix buisnes logic related key prefix for the cache
   * @param statusLoader status supplier, that used in case when status was not found in cache
   * @return status related to the provided revision
   */
  @Nullable
  public T getStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix, @NotNull Supplier<T> statusLoader) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return null;

    T status = getStatusFromCache(revision, prefix);
    if (status != null) {
      return status;
    }
    Lock lock = myCacheLocks.get(revision.getRevision());
    lock.lock();
    try {
      status = getStatusFromCache(revision, prefix);
      if (status != null) {
        return status;
      }

      T loadedStatus = statusLoader.get();
      if (loadedStatus != null) {
        putStatusToCache(revision, prefix, loadedStatus);
      }
      return loadedStatus;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns status from the cache. If there was no status in the cache for provided revision, it tries to load a batch of statuses using provided supplier and put them to cache
   * @param revision key for the cache
   * @param prefix buisnes logic related key prefix for the cache
   * @param batchStatusLoader statuses supplier, that used in case when status was not found in cache
   * @param prefixProvider method that extracts prefix from the received status
   * @return status related to the provided revision
   */
  @Nullable
  public T getStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix,
                              @NotNull Supplier<Collection<T>> batchStatusLoader, @NotNull Function<T, String> prefixProvider) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return null;

    T status = getStatusFromCache(revision, prefix);
    if (status != null) {
      return status;
    }
    Lock lock = myCacheLocks.get(revision.getRevision());
    lock.lock();
    try {
      status = getStatusFromCache(revision, prefix);
      if (status != null) {
        return status;
      }

      Collection<T> loadedStatuses = batchStatusLoader.get();
      if (loadedStatuses != null && !loadedStatuses.isEmpty()) {
        putStatusesToCache(revision, loadedStatuses, prefixProvider);
      }
      return getStatusFromCache(revision, prefix);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Puts new value to the cache
   * @param revision key for the cache
   * @param prefix buisnes logic related key prefix for the cache
   * @param status new value to be added to the cache
   * @return previous value in cache or null
   */
  private void putStatusToCache(@NotNull BuildRevision revision, @Nullable String prefix, @NotNull T status) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    myCache.put(buildKey(revision, prefix), new ValueWithTTL<>(status, getTTL()));
  }

  private void putStatusesToCache(@NotNull BuildRevision revision, @NotNull Collection<T> statuses, Function<T, String> prefixProvider) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    for (T status : statuses) {
      String prefix = prefixProvider.apply(status);
      String key = buildKey(revision, prefix);
      myCache.put(key, new ValueWithTTL<>(status, getTTL()));
    }
  }

  /**
   * Removes value from the cache
   * @param revision key for the cache
   * @param prefix buisnes logic related key prefix for the cache
   * @return removed from the cache value
   */
  public void removeStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    myCache.remove(buildKey(revision, prefix));
  }

  public void cleanupCache() {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    myCache.values().removeIf(val -> !val.isAlive());
  }

  @NotNull
  private String buildKey(@NotNull BuildRevision revision, @Nullable String prefix) {
    return prefix == null ? revision.getRevision() : prefix + ":" + revision.getRevision();
  }

  private long getTTL() {
    return System.currentTimeMillis() + TeamCityProperties.getIntervalMilliseconds(CACHE_VALUE_TTL_PAPRAMETER, CAHCE_VALUE_TTL_DEFAULT_VALUE_MS);
  }
}
